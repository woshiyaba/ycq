## Requirement

修复 Docker Compose 首次启动时，业务服务早于 MySQL 初始化完成而触发 Hikari `Communications link failure` 的问题。

## Implementation

为 MySQL 增加 TCP 健康检查，并让直接访问数据库的 `user-service`、`goods-service` 和 `im-service` 等待 MySQL 健康后再启动。Compose 文件版本调整为支持条件依赖的 `2.4`。

## Affected Areas

- Docker Compose 本地全栈启动顺序。
- MySQL 首次初始化及导入测试数据期间的业务服务启动行为。

## Verification

- 检查三个数据库服务都使用 `service_healthy` 条件依赖。
- `git diff --check` 通过（仅提示 Windows 工作区将在 Git 下次处理时把 LF 转为 CRLF）。
- 当前执行环境未安装 Docker CLI，未运行容器级验证；需在 Docker 环境执行 `docker compose config` 和 `docker compose up --build`。
