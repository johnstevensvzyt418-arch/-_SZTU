import torch
import torch.nn as nn


class MLP_AE(nn.Module):
    def __init__(self, input_size, hidden_size, dropout):
        super(MLP_AE, self).__init__()
        # Encoder
        self.fc1 = nn.Linear(input_size, int(hidden_size/4))
        self.fc2 = nn.Linear(int(hidden_size/4), int(hidden_size/2))
        self.fc3 = nn.Linear(int(hidden_size/2), hidden_size)

        # Decoder
        self.fc4 = nn.Linear(hidden_size, int(hidden_size/2))
        self.fc5 = nn.Linear(int(hidden_size/2), int(hidden_size/4))
        self.fc6 = nn.Linear(int(hidden_size/4), input_size)

        self.relu = nn.ReLU()
        self.dropout = nn.Dropout(dropout)
        self.layer_norm1 = nn.LayerNorm(int(hidden_size/4))
        self.layer_norm2 = nn.LayerNorm(int(hidden_size/2))

    def forward(self, x):
        # Encoder
        out = self.fc1(x)
        out = self.layer_norm1(out)
        out = self.fc2(out)
        out = self.dropout(self.relu(out))
        out = self.fc3(out)


        # Decoder
        out = self.fc4(out)
        out = self.layer_norm2(out)
        out = self.fc5(out)
        out = self.dropout(self.relu(out))
        out = self.fc6(out)


        return out


if __name__ == '__main__':
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    model = MLP_AE(5, 256, 0.3).to(device)
    input_data = torch.randn(1, 18, 5).to(device)
    print("input_shape:", input_data.shape)
    output = model(input_data)
    print("output_shape:", output.shape)
