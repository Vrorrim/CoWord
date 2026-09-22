# English Word Bank Demo

一个本地优先的英语单词库桌面原型。当前演示以下流程：

- 首次/随时选择词库数据目录；数据会保存为该目录中的 `EnglishWordBank/word-bank.db`。
- 添加单词、第一认知和基础释义。
- 新增单词后，与已存单词计算形近分数；阈值可选择宽松、平衡或严格。
- 词库数据与程序工程分离，便于备份和迁移。

## 运行要求

- Windows
- JDK 25（开发运行时）
- Maven 3.9+

## 启动

在项目根目录运行：

```powershell
mvn "-Dmaven.repo.local=$PWD\.mvn-local-repository" javafx:run
```

> `.mvn-local-repository` 仅是本项目的 Maven 依赖缓存，不是用户的词库数据，可以随时重新下载。

## 体验建议

依次加入 `derive` 与 `deprive`，保持“平衡”形近敏感度，即可看到系统识别到这对形近词。随后添加 `deposit`，并写下“和钱、银行有关”的第一认知。

## 下一步

1. 接入正式词典服务，替换演示用词典资料。
2. 将“本次发现的关联”改为可确认、删除和编辑的关系卡片。
3. 接入国内大模型 API，提供语境释义、短语解释和长难句拆解。
4. 使用 `jpackage` 生成无需用户安装 Java 的 Windows 安装包。
