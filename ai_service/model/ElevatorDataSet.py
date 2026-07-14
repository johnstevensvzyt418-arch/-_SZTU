# Dataset类，用于加载数据集
from torch.utils.data import Dataset
import pandas as pd
import os
import torch


def divide_ABC_xinshida(file_name):
    # 读取 CSV 文件
    df = pd.read_csv(file_name).iloc[:, :5]

    df.columns = ["feature1", "feature2", "feature3", "feature4", "feature5"]

    # 将第五列上移一行, 最后一行补1
    df['feature5'] = df['feature5'].shift(-1)
    df.loc[len(df) - 1, 'feature5'] = 1

    # 添加一列，用于存储 feature1 的值, 避免标准化之后丢失分割信息
    df['feature6'] = df['feature1']

    # 存储分组数据的列表
    groups = []
    index_list = []

    # 按照门状态进行分组3(xinshida)
    for i in range(len(df) - 2):
        if (df.loc[i:i + 1, 'feature6'] == [3, 1]).all():
            index_list.append(i + 2)
        elif (df.loc[i:i + 1, 'feature6'] == [2, 1]).all():
            index_list.append(i + 2)

    # 添加最后一个 index, 避免最后一个分组丢失
    index_list.append(len(df))

    # 根据切分点，将数据切分为多个运动
    start = 0
    for idx in index_list:
        current_group = df.iloc[start:idx, :5]

        # 将每次运动的第一条数据的 feature5 重置为 1
        current_group.loc[start, 'feature5'] = 1

        groups.append(current_group)
        start = idx

    return groups


def divide_ABC_zhuyun(file_name):
    # 读取 CSV 文件
    df = pd.read_csv(file_name).iloc[:, :4]
    df.columns = ["feature1", "feature2", "feature3", "feature4"]

    # 将第四列上移一行, 最后一行补1
    df['feature4'] = df['feature4'].shift(-1)
    df.loc[len(df) - 1, 'feature4'] = 1

    # 添加一列，用于存储 feature1 的值, 避免标准化之后丢失分割信息
    df['feature5'] = df['feature1']

    # 存储分组数据的列表
    groups = []
    index_list = []

    # 按照门状态进行分组，找到每个分割点的 index
    for i in range(len(df) - 2):
        if (df.loc[i:i + 1, 'feature5'] == [4, 0]).all():
            index_list.append(i + 2)
        elif (df.loc[i:i + 1, 'feature5'] == [12, 0]).all():
            index_list.append(i + 2)
        elif (df.loc[i:i + 1, 'feature5'] == [8, 0]).all():
            index_list.append(i + 2)

    # 添加最后一个 index，避免最后一个分组丢失
    index_list.append(len(df))

    # 根据切分点，将数据切分为多个运动
    start = 0
    for idx in index_list:
        current_group = df.iloc[start:idx, :4]

        # 将每次运动的第一条数据的 feature4 重置为 0
        current_group.loc[start, 'feature4'] = 0
        groups.append(current_group)
        start = idx
    return groups


class ElevatorDataSet(Dataset):
    def __init__(self, path, dataset_name):
        self.data_list = []
        self.data_path = path
        self.files = os.listdir(self.data_path)  # 获取文件夹下的所有文件

        # 读取每一个csv文件
        for file in self.files:
            ABC_groups = []
            each_file_path = os.path.join(self.data_path, file)
            print("正在读取：", each_file_path, "...")

            # 读取不同数据集的数据
            if dataset_name == 'xinshida':
                ABC_groups = divide_ABC_xinshida(each_file_path)
            elif dataset_name == 'zhuyun':
                ABC_groups = divide_ABC_zhuyun(each_file_path)

            self.data_list.extend(ABC_groups)

        # 将data_list中的数据转换为tensor
        for i in range(len(self.data_list)):
            self.data_list[i] = torch.tensor(self.data_list[i].values, dtype=torch.float32)

    def __len__(self):
        return len(self.data_list)

    def __getitem__(self, idx):
        return self.data_list[idx]


if __name__ == '__main__':
    data_path = '../data/xinshida/Train'
    dataset = ElevatorDataSet(data_path, 'xinshida')
    print(len(dataset))
    print(dataset[0])
    print(dataset[1])
    print(dataset[2])
    print(dataset[3])


