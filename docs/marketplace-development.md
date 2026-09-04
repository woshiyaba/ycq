# 运城圈基础功能开发说明

项目继续使用 Java 8、Maven 多模块 Spring Cloud 和原生微信小程序。新增业务集中在现有 `goods-service`，用户资料由 `user-service` 提供，私信复用 `im-service`，登录继续使用微信登录和 JWT。

| 功能 | 小程序入口 | 后端入口 |
| --- | --- | --- |
| 首页推荐、关注、分类筛选、精选、搜索 | 首页「圈」、精选 | `/index/feed` |
| 社区动态、发布及个人内容 | 生活动态发布、内容详情、个人主页 | `/post/entries`、`/post/mine` |
| 招聘列表、周结/日结、兼职、行业筛选 | 招聘列表、招聘发布、职位详情 | `/post/entries?kind=RECRUITMENT` |
| 文字图片发布、编辑、上下架、删除 | 发布页、自己的内容详情 | `/post/entries/{id}`、`/{id}/status` |
| 点赞、收藏、留言、回复 | 社区/招聘详情、我的收藏 | `/post/entries/{id}/like`、`/favorite`、`/comments`、`/post/favorites` |
| 评论回复提醒、文字私信 | 消息页、详情中的「聊一聊」 | `/post/notifications`、`/post/entries/{id}/chat`、`/chat/**`、`/ws/**` |
| 个人资料、关注、地址、足迹 | 我的、个人主页、收货地址 | `/goodsUser/profile`、`/follow/{id}`、`/following`、`/addresses`、`/history` |
| 商品交易、模拟付款、发货、收货、评价 | 商品详情、订单确认、我的订单 | `/goods/orders`、`/goods/orders/{id}/{pay,cancel,ship,receive,review}` |

社区与招聘共用内容主表、留言及互动表，招聘专属字段保存在扩展表中。留言列表是带 `parentId`、`replyCommentId` 的分页扁平列表。评论通知同时读取社区/招聘留言和原商品留言：`source` 区分 `CONTENT`、`GOODS`，已读接口分别使用 `ids`/`goodsIds` 和 `maxId`/`goodsMaxId`。编辑后的昵称头像保存在 `user_profile` 覆盖层，微信重新登录仍保留这些设置。

数据库增量文件位于 `dev/mysql/migrations/`：

| 文件 | 变更 |
| --- | --- |
| `20260904_account.sql` | 用户资料覆盖、关注、地址、足迹；商品留言 `read_at`；会话 `post_id` 与唯一约束 |
| `20260904_content.sql` | `content_post`、`recruitment_job`、`content_comment`、`content_reaction` |
| `20260904_orders.sql` | `goods_order`、`goods_order_review`，包含幂等请求及有效订单唯一约束 |

迁移面向已具有原始 `user_service`、`goods_service`、`im_service` 库表的 MySQL 5.7 环境。已有数据库使用增量脚本；`dev/mysql/dump/` 中的完整初始化脚本含重建表语句，仅供新环境初始化。Docker 的初始化目录只在新数据卷首次启动时运行，后续升级仍需执行增量迁移。

`dev/mysql/migrate.py` 需要 Python 3 和 PyMySQL。它默认只列出文件，提供 `--apply` 后按文件名顺序执行。以下为 PowerShell 用法，连接信息只从环境变量读取，密码输入不回显：

```powershell
$env:YCQ_DB_HOST = Read-Host '数据库主机'
$env:YCQ_DB_USER = Read-Host '数据库用户'
$env:YCQ_DB_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host '数据库密码' -AsSecureString)).Password
# 非默认端口时设置 YCQ_DB_PORT；未设置时使用 3306。
python dev/mysql/migrate.py
python dev/mysql/migrate.py --apply
```

缺少驱动时先执行 `python -m pip install PyMySQL`。迁移使用 MySQL DDL 和自动提交，不具备事务回滚；失败时应检查报错后重跑。脚本不会删除已有业务记录。

运行依赖 MySQL、Redis、RabbitMQ，以及 Eureka、`user-service`、`auth-service`、`goods-service`、`im-service`、gateway 六个应用。gateway 对小程序提供 HTTP 和 WebSocket 入口，Compose 暴露端口为 8080。分别检查各应用的 `application-dev.yml` / `application-docker.yml`，把目标环境配置传入对应进程或容器；Docker 镜像使用 Java 8 运行时。

`YCQ_DB_*` 仅供迁移脚本使用。启动 Java 服务时用 Spring 原生环境变量 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 覆盖数据库配置；三个服务的 JDBC URL 分别指向 `user_service`、`goods_service`、`im_service`。Redis、RabbitMQ 和 Eureka 继续使用各服务现有配置或对应的 Spring 环境变量。

微信凭据使用被忽略的 `auth-service/src/main/resources/application-secret.yml`；JWT 验证服务需要与认证服务匹配的公钥。图片上传复用腾讯 COS，需要提供 `TENCENT_COS_SECRET_ID`、`TENCENT_COS_SECRET_KEY`、`TENCENT_COS_BUCKET`、`TENCENT_COS_REGION`。本说明不保存任何连接地址或凭据。

构建前让 `JAVA_HOME` 指向 JDK 8，并把对应 `bin` 放在 `PATH` 首位。下面的启动顺序适用于已配置上述环境的 Compose 部署：

```powershell
mvn -version
mvn package '-Dmaven.compiler.source=1.8' '-Dmaven.compiler.target=1.8' '-Djava.version=1.8' -DskipTests
docker compose up -d mysql redis rabbitmq
# 等待 MySQL 健康检查通过后执行迁移。
python dev/mysql/migrate.py --apply
docker compose up -d --build
```

在微信开发者工具中导入 `wx-front/`，配置小程序 AppID 并使用「编译」。接口根地址由 `wx-front/config/api.js` 读取，可通过小程序缓存 `apiRoot` 指向当前环境的网关根 URL；修改后重新编译。正式小程序需配置与部署环境对应的 HTTPS/WSS 合法域名。该目录没有 npm 构建步骤。

首页和精选已按墨刀重做，复用现有图标。两页使用独立的 `templates/feed.wxml`、`styles/feed.wxss` 和 `utils/feed.js`，搜索、频道切换、分类筛选、下拉刷新与分页均请求 `/index/feed`。该接口接受 `scene=HOME|FEATURED`、`channel`、`categoryKey`、`keyword`、`page`、`size`，返回 `items/total/page/size/hasMore`，第一页附 `banners`。首页推荐、新品、热点及关注包含闲置与社区动态；广场、圈子、找资源展示社区内容，精选和服饰展示商品。关注需要登录，分类按服务器类目及其后代筛选，招聘入口复用招聘页面。

本次两页改版不新增库表，依赖上述已迁移表；需同步部署新的 `goods-service`。轮播、商品、作者及动态来自接口，墨刀示例文案和图片仅用于本地布局校验。自定义导航会为微信状态栏和胶囊按钮预留实际空间，最终高度须在微信开发者工具或真机检查。

支付目前为模拟通道：有效订单调用 `POST /goods/orders/{id}/pay` 后直接进入 `PAID`，界面提示「模拟支付成功」，不会调用微信支付或发生真实扣款。身份、订单归属、商品占用及状态仍由后端校验。订单金额来自商品价格和运费快照；重复创建、支付、收货和评价具有幂等处理。交易流程为 `PENDING → PAID → SHIPPED → COMPLETED`，待付款订单可取消。

以下为可复现的验证命令；本次执行结果见文末。

```powershell
# Java 8；只运行目标测试，避免启动完整 Spring Boot 环境。
mvn -pl goods-service,user-service,im-service -am '-Dmaven.compiler.source=1.8' '-Dmaven.compiler.target=1.8' '-Djava.version=1.8' '-Dtest=ContentServiceTests,OrderServiceTests,GoodsMutationTests,AccountServiceTests,ProfileControllerTests,ChatServiceTests,UploadControllerTests,PopularScoreSqlTests' '-Dsurefire.failIfNoSpecifiedTests=false' test

# 小程序页面注册、事件/导航、请求重试和 WebSocket 检查。
node wx-front/tests/smoke.js
```

真实数据库测试 `MarketplaceDatabaseTests` 使用已迁移的 `goods_service` 数据库，无需启动 Eureka、COS、Redis 或其他应用。只有 `YCQ_TEST_DB_URL`、`YCQ_TEST_DB_USER`、`YCQ_TEST_DB_PASSWORD` 齐备时运行，否则自动跳过。它使用固定测试用户标识，在同一事务中建立测试数据，结束或断言失败时自动回滚，并检查新增记录已消失；MySQL 自增编号可能留下间隙。

```powershell
$env:YCQ_TEST_DB_URL = Read-Host '指向 goods_service 的 JDBC URL'
$env:YCQ_TEST_DB_USER = Read-Host '数据库测试用户'
$env:YCQ_TEST_DB_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host '数据库测试密码' -AsSecureString)).Password
mvn -pl goods-service -am '-Dmaven.compiler.source=1.8' '-Dmaven.compiler.target=1.8' '-Djava.version=1.8' '-Dtest=MarketplaceDatabaseTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

数据库测试覆盖实际内容/招聘映射、回复、两源通知、点赞收藏、关注、默认地址、足迹封面、个人统计，以及订单创建到评价的完整 SQL 流程。上线前仍需在微信开发者工具或真机验证登录、图片上传、消息收发、页面交互及视觉效果。

2026-09-05 首页/精选改版验证：`FeedServiceTests` 的 3 项测试及 `MarketplaceDatabaseTests#homeAndFollowingFeedsMapMixedSourcesAndRealPopularityWithPagination` 通过，后者实际连接 MySQL 验证混合流、图集、粉丝、分页及互动热度排序，数据自动回滚。使用 Java 8 清理并重建 `common,inner-api,goods-service` 成功；小程序 smoke 与 31 个 WXML/WXSS 文件原生编译通过。通过实际 WXML 编译树和 WXSS 完成 393px 浏览器布局对照，该预览使用临时样例内容，不代替微信真机验收。

2026-09-04 验证记录：使用 JDK 8 从源码执行 `clean package` 和上述目标测试，全部 9 个 Maven 模块构建成功，32 项测试通过且无跳过，其中 2 项数据库集成测试实际执行并回滚。额外在实际 MySQL 中验证了聊天分页边界的 28 条同秒消息完整返回，测试会话及消息均已回滚。小程序 smoke 检查、28 页及模板和自定义底栏的微信原生 WXML/WXSS 编译通过。

当前代码及 JAR 已更新，数据库增量已执行；服务端 JAR 尚未部署到远程服务器，微信真机验收尚未执行。部署时需要同步更新 `user-service`、`goods-service`、`im-service`，避免新版小程序调用旧接口。私信沿用单实例 Redis 未读队列和 MySQL 已读历史；跨存储异常时仍可能重复落库，若需要严格消息去重或多实例部署，再增加持久化消息唯一键与对应并发控制。
