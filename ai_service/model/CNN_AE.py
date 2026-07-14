import torch
import torch.nn as nn


class CNN_AE(nn.Module):
    def __init__(self, input_size, hidden_channels):
        super(CNN_AE, self).__init__()
        hc = hidden_channels
        # Encoder: 逐步压缩特征维度 5 → 1
        self.conv1 = nn.Conv2d(in_channels=1, out_channels=hc//2, kernel_size=(1, 5))
        self.conv2 = nn.Conv2d(in_channels=hc//2, out_channels=hc//4, kernel_size=(1, 1))

        # Decoder: 转置卷积镜像还原 1 → 5
        self.conv3 = nn.ConvTranspose2d(in_channels=hc//4, out_channels=hc//2, kernel_size=(1, 1))
        self.conv4 = nn.ConvTranspose2d(in_channels=hc//2, out_channels=1, kernel_size=(1, 5))

        self.relu = nn.ReLU()
        self.layer_norm = nn.LayerNorm(input_size)

    def forward(self, seq):
        # Encoder
        seq = seq.unsqueeze(1)                # (B, seq_len, 5) → (B, 1, seq_len, 5)
        out = self.conv1(seq)                 # (B, 64, seq_len, 1)
        out = self.relu(out)
        out = self.conv2(out)                 # (B, 32, seq_len, 1)
        out = self.relu(out)

        # Decoder — 转置卷积还原
        out = self.conv3(out)                 # (B, 64, seq_len, 1)
        out = self.relu(out)
        out = self.conv4(out)                 # (B, 1, seq_len, 5)
        out = self.relu(out).squeeze(1)   # (B, 1, seq_len, 5) → (B, seq_len, 5)
        return out


if __name__ == '__main__':
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    model = CNN_AE(5, 128).to(device)
    input_data = torch.randn(1, 18, 5).to(device)
    print("input_shape:", input_data.shape)
    output = model(input_data)
    print("output_shape:", output.shape)
