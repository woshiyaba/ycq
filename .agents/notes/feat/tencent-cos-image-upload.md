## Requirement

新增无需鉴权的项目内图片上传接口，使用腾讯云 COS Java SDK 保存图片并返回访问地址；小程序停止使用外部图床，统一改用该接口。

## Implementation

在 `goods-service` 中通过 Maven 接入 COS SDK，使用环境变量创建并复用 `COSClient`。新增 `POST /upload/image`，接收 `file`，校验 5MB 上限及 JPEG/PNG 文件头，以 UUID 生成对象键并返回 COS 默认公有读 URL。网关增加上传路由，小程序发布页改为调用该接口，同时修复压缩后仍上传原图的问题。

## Affected Areas

- 商品服务的 COS 配置、multipart 限制和公开上传接口。
- 网关的 `/upload/**` 路由。
- 小程序发布商品时的图片选择、压缩、上传和错误提示。
- Docker Compose 与 README 中的 COS 环境变量说明。

## Verification

- 使用 mock COS 客户端的单元测试覆盖 JPEG/PNG 上传、非法或超限文件以及 SDK 异常。
- 使用 Java 8 运行 `mvn -pl goods-service,gateway -am test`，测试全部通过。
- `node --check` 检查两个修改过的小程序 JavaScript 文件，均通过。
- `git diff --check` 通过，并确认小程序中不再包含 `sm.ms` 上传地址。
- 当前环境未安装 Docker CLI，未执行 `docker compose config`。
- 真实 COS 上传需要配置有效环境变量后进行联调。
