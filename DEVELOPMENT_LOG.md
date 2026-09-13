# Backend Development Log

本文件是后端任务索引。详细实现和验证写入 `docs/handoffs/<TASK-ID>.md`，代码审查记录以对应 PR 为准。

## 使用规则

- 每个后端任务一行，Agent 只新增或更新自己的任务行。
- 开工时登记 `IN_PROGRESS`；PR 转为可审查后登记 `REVIEW`；只有项目负责人在 PR 合并后登记 `DONE`。
- 分支、PR、交接报告、契约版本和验证结果必须可以相互追踪。
- 发生并行合并冲突时保留双方记录，不得整文件覆盖。
- 长日志、错误堆栈和测试输出放入交接报告或 PR，不粘贴到本索引。

## 状态索引

| 任务 ID | 分支 | 执行者 | 状态 | 开始日期 | PR | 交接报告 | 契约/迁移基线 | 最近更新 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| BE-01 | `main` | 项目负责人 | DONE | 2026-09-06 | 历史初始化，无 PR | 无 | Spring Boot 4.1.1 / Java 21 | 2026-09-06 |
| BE-02 | `main` | 项目负责人 / Codex | DONE | 2026-09-06 | 历史初始化，无 PR | 无 | Maven 依赖基线 | 2026-09-06 |
| PM-03 | `docs/PM-03-collaboration-visibility` | Codex | DONE | 2026-09-06 | [#2](https://github.com/tednved/graduation-backend/pull/2) | `docs/handoffs/PM-03.md` | 无契约变化 | 2026-09-10 |
| DB-01 | `feat/DB-01-initial-schema` | 数据库 Agent | DONE | 2026-09-10 | [#1](https://github.com/tednved/graduation-backend/pull/1) | `docs/handoffs/DB-01.md` | Flyway V1～V5 | 2026-09-10 |
| DB-02 | `fix/DB-02-identifier-collations` | 数据库 Agent | DONE | 2026-09-10 | [#3](https://github.com/tednved/graduation-backend/pull/3) | `docs/handoffs/DB-02.md` | Flyway V6 | 2026-09-10 |
| API-01 | `docs/API-01-openapi-contract` | API 契约 Agent / Codex | DONE | 2026-09-10 | [#4](https://github.com/tednved/graduation-backend/pull/4) | `docs/handoffs/API-01.md` | OpenAPI 3.1 / Flyway V6 `bea0f7b8` | 2026-09-12 |
| BE-MVP-01 | `feat/BE-MVP-01-core` | Claude | DONE | 2026-09-11 | [#5](https://github.com/tednved/graduation-backend/pull/5) | `docs/handoffs/BE-MVP-01.md` | API-01 `e15baa77` / Flyway V6 | 2026-09-12 |
| BE-MVP-02 | `feat/BE-MVP-02-item-favorite` | Claude | DONE | 2026-09-12 | [#6](https://github.com/tednved/graduation-backend/pull/6) | `docs/handoffs/BE-MVP-02.md` | 合并提交 `f4ce306`；真实 MySQL 36/36、联调 22/22、GUI 冒烟通过 | 2026-09-12 |
| BE-MVP-03 | `feat/BE-MVP-03-trade` | Claude | DONE | 2026-09-13 | [#7](https://github.com/tednved/graduation-backend/pull/7) | `docs/handoffs/BE-MVP-03.md` | 合并提交 `b4b622d`；真实 MySQL 60/60、契约 51 操作、负责人 GUI 验收通过 | 2026-09-13 |

## 合并后更新要求

项目负责人合并 PR 后，将对应行改为 `DONE`，把 PR 填为可访问链接，并在“最近更新”中记录合并日期。已经结束的历史行只允许补充链接或纠正事实，不得删除。
