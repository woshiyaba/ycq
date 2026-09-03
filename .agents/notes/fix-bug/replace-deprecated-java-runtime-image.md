## Requirement

修复后端服务因弃用的 `openjdk:8-jre` 基础镜像而无法稳定构建的问题，同时避免在 Dockerfile 中绑定特定镜像加速服务。

## Implementation

将六个后端服务的基础镜像统一替换为 `eclipse-temurin:8-jre`，并使用 `LABEL maintainer` 替代已弃用的 `MAINTAINER` 指令。

## Affected Areas

- `auth-service`、`eureka`、`gateway`、`goods-service`、`im-service` 和 `user-service` 的容器构建。
- README 中的 Docker Compose 部署说明。

## Verification

- 检查六个 Dockerfile 均使用 `eclipse-temurin:8-jre` 和 `LABEL maintainer`。
- 检查仓库中不再存在 `openjdk:8-jre` 或 `MAINTAINER` Dockerfile 指令。
- 运行 `git diff --check` 检查补丁格式。
