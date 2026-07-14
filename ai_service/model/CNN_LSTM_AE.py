import torch
import torch.nn as nn


class CNN_LSTM_AE(nn.Module):
    def __init__(self, input_size, hidden_size, num_layers, out_channels):
        super(CNN_LSTM_AE, self).__init__()
        self.conv1 = nn.Conv2d(in_channels=1, out_channels=out_channels, kernel_size=(1, 5))
        self.relu = nn.ReLU()
        self.conv2 = nn.Conv2d(in_channels=out_channels, out_channels=input_size, kernel_size=(1, 1))
        self.lstm = nn.LSTM(input_size, hidden_size, num_layers, batch_first=True)
        self.layer_norm = nn.LayerNorm(hidden_size)
        self.fc0 = nn.Linear(hidden_size, hidden_size)
        self.fc1 = nn.Linear(hidden_size, int(hidden_size/2))
        self.fc2 = nn.Linear(int(hidden_size/2), input_size)

    def forward(self, seq):
        seq = seq.unsqueeze(1)
        out = self.conv1(seq)
        out = self.relu(out)
        out = self.conv2(out)
        out = self.relu(out)
        out = out.squeeze(3).permute(0, 2, 1)
        out, (h, c) = self.lstm(out)
        hidden = torch.sum(h, dim=0) + torch.sum(c, dim=0)
        hidden = self.fc0(hidden)
        hidden = self.relu(hidden)
        hidden = hidden.unsqueeze(0)
        out = out + hidden
        out = self.layer_norm(out)
        out = self.fc1(out)
        out = self.relu(out)
        out = self.fc2(out)
        return out


if __name__ == '__main__':
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    model = CNN_LSTM_AE(5, 128, 2, 64).to(device)
    input_data = torch.randn(1, 18, 5).to(device)
    print("input_shape:", input_data.shape)
    output = model(input_data)
    print("output_shape:", output.shape)
