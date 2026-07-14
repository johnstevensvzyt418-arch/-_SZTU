import random


class DataLoader:
    def __init__(self, dataset, batch_size, shuffle=False, drop_last=False):
        self.current_index = None
        self.dataset = dataset
        self.batch_size = batch_size
        self.shuffle = shuffle
        self.drop_last = drop_last
        self.indices = list(range(len(dataset)))
        self.reset()

    def reset(self):
        """重置迭代器"""
        self.current_index = 0
        if self.shuffle:
            random.shuffle(self.indices)

    def __iter__(self):
        """每次调用时重置索引"""
        self.reset()
        return self

    def __next__(self):
        if self.current_index >= len(self.dataset):
            raise StopIteration

        end_index = min(self.current_index + self.batch_size, len(self.dataset))
        batch_indices = self.indices[self.current_index:end_index]
        batch = [self.dataset[i] for i in batch_indices]

        self.current_index += self.batch_size

        # 如果启用 drop_last 并且当前批次小于 batch_size，则跳过该批次
        if self.drop_last and len(batch) < self.batch_size:
            raise StopIteration

        return batch


# 示例用法
if __name__ == "__main__":
    from ElevatorDataSet import ElevatorDataSet

    train_data_path = '../data/zhuyun/Train'
    # 创建一个示例数据集
    train_dataset = ElevatorDataSet(train_data_path)

    # 初始化 DataLoader
    dataloader = DataLoader(train_dataset, batch_size=32, shuffle=True, drop_last=False)

    # 循环获取两次数据
    for epoch in range(2):
        print(f"Epoch {epoch + 1}")
        for batc in dataloader:
            print(batc)
