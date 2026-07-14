import torch
import torch.nn as nn


class LSTM_AE(nn.Module):
    def __init__(self, input_size, hidden_size, num_layers, dropout):
        super(LSTM_AE, self).__init__()

        self.encoder = nn.LSTM(input_size, hidden_size, num_layers, batch_first=True, dropout=dropout)
        self.decoder = nn.LSTM(input_size, hidden_size, num_layers, batch_first=True, dropout=dropout)

        self.fc_out = nn.Linear(hidden_size, input_size)
        self.layer_norm_encoder = nn.LayerNorm(hidden_size)
        self.layer_norm_decoder = nn.LayerNorm(hidden_size)

    def forward(self, x):
        outputs = []

        length = x.size(1)
        encoder_output, encoder_hidden = self.encoder(x)

        # 将encoder的输出的最后一个时间步的输出作为decoder的输入
        last_encoder_output = encoder_output[:, -1, :]

        decoder_input = self.layer_norm_encoder(last_encoder_output)
        decoder_input = self.fc_out(decoder_input).unsqueeze(1)

        # outputs.append(decoder_input)

        decoder_h = encoder_hidden[0]
        decoder_c = encoder_hidden[1]

        # 打印形状
        # print("encoder_output_shape:", encoder_output.shape)
        # print("decoder_input_shape:", decoder_input.shape)
        # print("decoder_h_shape:", decoder_h.shape)
        # print("decoder_c_shape:", decoder_c.shape)

        for i in range(length):
            out, hidden = self.decoder(decoder_input, (decoder_h, decoder_c))
            decoder_h = hidden[0]
            decoder_c = hidden[1]

            out = out.squeeze(1)
            out = self.layer_norm_decoder(out)
            out = self.fc_out(out.squeeze(1))
            decoder_input = out.unsqueeze(1)
            outputs.append(out.unsqueeze(1))

        return torch.flip(torch.cat(outputs, dim=1), [1])


if __name__ == '__main__':
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    model = LSTM_AE(5, 128, 1, 0).to(device)
    input_data = torch.randn(1, 20, 5).to(device)
    print("input_shape:", input_data.shape)
    output = model(input_data)
    print("output_shape:", output.shape)
