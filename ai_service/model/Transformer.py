import torch
import torch.nn as nn

class RelativePositionEmbedding(nn.Module):
    def __init__(self, embed_dim, max_seq_len):
        super(RelativePositionEmbedding, self).__init__()
        self.embed_dim = embed_dim
        self.position_embeddings = nn.Embedding(2 * max_seq_len - 1, embed_dim)
        self.max_seq_len = max_seq_len

    def forward(self, length, device):
        range_vec = torch.arange(length, dtype=torch.long, device=device)
        range_vec = range_vec.view(1, -1) - range_vec.view(-1, 1)
        range_vec += self.max_seq_len - 1
        range_vec = torch.clamp(range_vec, 0, 2 * self.max_seq_len - 2)
        pos_embed = self.position_embeddings(range_vec)
        return pos_embed

class AttentionWithRelativePosition(nn.Module):
    def __init__(self, embed_dim, num_heads, max_seq_len):
        super(AttentionWithRelativePosition, self).__init__()
        self.embed_dim = embed_dim
        self.num_heads = num_heads
        self.head_dim = embed_dim // num_heads
        self.max_seq_len = max_seq_len

        self.pos_embeddings = RelativePositionEmbedding(embed_dim, max_seq_len)

        self.qkv = nn.Linear(embed_dim, embed_dim * 3)
        self.fc = nn.Linear(embed_dim, embed_dim)
        self.scale = self.head_dim ** 0.5

    def forward(self, x):
        batch_size, seq_length, embed_dim = x.size()
        q, k, v = self.qkv(x).split(embed_dim, dim=-1)

        q = q.view(batch_size, seq_length, self.num_heads, self.head_dim).permute(0, 2, 1, 3)
        k = k.view(batch_size, seq_length, self.num_heads, self.head_dim).permute(0, 2, 1, 3)
        v = v.view(batch_size, seq_length, self.num_heads, self.head_dim).permute(0, 2, 1, 3)

        # 相对位置编码: (seq, seq, embed_dim) → (1, heads, seq, seq, head_dim)
        pos_embed = self.pos_embeddings(seq_length, x.device)
        pos_embed = pos_embed.view(1, seq_length, seq_length, self.num_heads, self.head_dim)
        pos_embed = pos_embed.permute(0, 3, 1, 2, 4)  # (1, heads, seq, seq, head_dim)

        # 内容注意力 + 位置偏置（用 einsum 避免维度广播问题）
        attn_content = torch.matmul(q, k.transpose(-2, -1)) / self.scale
        pos_bias = torch.einsum('bhnd,bhmnd->bhnm', q, pos_embed) / self.scale
        attn = (attn_content + pos_bias).softmax(dim=-1)

        # 输出 = 内容加权 + 位置加权
        out_content = torch.matmul(attn, v)
        out_pos = torch.einsum('bhnm,bhmnd->bhnd', attn, pos_embed)
        out = out_content + out_pos

        out = out.permute(0, 2, 1, 3).contiguous().view(batch_size, seq_length, embed_dim)
        out = self.fc(out)
        return out

class EncoderLayer(nn.Module):
    def __init__(self, embed_dim, num_heads, max_seq_len, dropout=0.1):
        super(EncoderLayer, self).__init__()
        self.attention = AttentionWithRelativePosition(embed_dim, num_heads, max_seq_len)
        self.norm1 = nn.LayerNorm(embed_dim)
        self.dropout1 = nn.Dropout(dropout)
        self.ffn = nn.Sequential(
            nn.Linear(embed_dim, embed_dim * 4),
            nn.GELU(),
            nn.Linear(embed_dim * 4, embed_dim)
        )
        self.norm2 = nn.LayerNorm(embed_dim)
        self.dropout2 = nn.Dropout(dropout)

    def forward(self, x):
        attn_out = self.attention(x)
        x = self.norm1(x + self.dropout1(attn_out))
        ffn_out = self.ffn(x)
        x = self.norm2(x + self.dropout2(ffn_out))
        return x

class DecoderLayer(nn.Module):
    def __init__(self, embed_dim, num_heads, max_seq_len, dropout=0.1):
        super(DecoderLayer, self).__init__()
        self.self_attention = AttentionWithRelativePosition(embed_dim, num_heads, max_seq_len)
        self.norm1 = nn.LayerNorm(embed_dim)
        self.dropout1 = nn.Dropout(dropout)
        self.encoder_attention = AttentionWithRelativePosition(embed_dim, num_heads, max_seq_len)
        self.norm2 = nn.LayerNorm(embed_dim)
        self.dropout2 = nn.Dropout(dropout)
        self.ffn = nn.Sequential(
            nn.Linear(embed_dim, embed_dim * 4),
            nn.GELU(),
            nn.Linear(embed_dim * 4, embed_dim)
        )
        self.norm3 = nn.LayerNorm(embed_dim)
        self.dropout3 = nn.Dropout(dropout)

    def forward(self, x, encoder_out):
        self_attn_out = self.self_attention(x)
        x = self.norm1(x + self.dropout1(self_attn_out))
        encoder_attn_out = self.encoder_attention(encoder_out)
        x = self.norm2(x + self.dropout2(encoder_attn_out))
        ffn_out = self.ffn(x)
        x = self.norm3(x + self.dropout3(ffn_out))
        return x

class Encoder(nn.Module):
    def __init__(self, embed_dim, num_heads, max_seq_len, num_layers, dropout=0.1):
        super(Encoder, self).__init__()
        self.layers = nn.ModuleList([EncoderLayer(embed_dim, num_heads, max_seq_len, dropout) for _ in range(num_layers)])

    def forward(self, x):
        for layer in self.layers:
            x = layer(x)
        return x

class Decoder(nn.Module):
    def __init__(self, embed_dim, num_heads, max_seq_len, num_layers, dropout=0.1):
        super(Decoder, self).__init__()
        self.layers = nn.ModuleList([DecoderLayer(embed_dim, num_heads, max_seq_len, dropout) for _ in range(num_layers)])

    def forward(self, x, encoder_out):
        for layer in self.layers:
            x = layer(x, encoder_out)
        return x

class TransformerAE(nn.Module):
    def __init__(self, embed_dim, num_heads, max_seq_len, num_layers, num_features, dropout=0.1):
        super(TransformerAE, self).__init__()
        self.embed_dim = embed_dim
        self.max_seq_len = max_seq_len
        self.num_features = num_features

        self.embedding = nn.Linear(num_features, embed_dim)
        self.encoder = Encoder(embed_dim, num_heads, max_seq_len, num_layers, dropout)
        self.decoder = Decoder(embed_dim, num_heads, max_seq_len, num_layers, dropout)
        self.output = nn.Linear(embed_dim, num_features)

    def forward(self, x):
        x_embed = self.embedding(x)
        encoder_out = self.encoder(x_embed)
        decoder_out = self.decoder(x_embed, encoder_out)
        output = self.output(decoder_out)
        return output

if __name__ == '__main__':
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    model = TransformerAE(embed_dim=64, num_heads=4, max_seq_len=128, num_layers=2, num_features=5).to(device)
    input_data = torch.randn(2, 18, 5).to(device)
    print("input_shape:", input_data.shape)
    output = model(input_data)
    print("output_shape:", output.shape)