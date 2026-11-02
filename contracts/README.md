# 数据契约

模组与后端之间交换数据的版本化 schema；当前已定义初版本机 API 形状，完整 schema 待补充。

> 权威文档见 [`docs/contracts/index.md`](../docs/contracts/index.md)。本文只作指针。

原则：保持技术中立；模组与后端各自独立实现契约，不共享实现类；变更需兼容或显式升级版本。