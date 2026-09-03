## Requirement

修复商品热度排序将时间戳直接相减，导致历史商品触发 MySQL EXP() DOUBLE 溢出的问题，并覆盖所有使用该排序算法的查询。

## Implementation

使用 TIMESTAMPDIFF(SECOND, last_edit, NOW()) 计算真实时间差，按原有“每 10 天指数衰减”的规则换算；将除以正指数改写为乘以负指数，并将未来时间钳制为零，避免指数上溢及异常时间导致商品热度被放大。搜索 Mapper 直接复用商品 Mapper 的表达式，保持单一修复点。

## Affected Areas

- 首页、分类及相似商品的热度排序。
- 关键词搜索结果的热度排序。

## Verification

- 新增单元测试，确保两个 Mapper 使用相同且不会直接相减时间戳的热度表达式。
- 使用 Java 8 运行 `mvn -pl goods-service -am test`，2 个测试全部通过。
- `git diff --check` 通过。
- 当前环境未安装 Docker CLI，未执行 MySQL 容器级查询验证。
