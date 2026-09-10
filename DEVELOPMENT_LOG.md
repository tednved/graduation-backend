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
| PM-03 | `docs/PM-03-collaboration-visibility` | Codex | REVIEW | 2026-09-06 | [#2](https://github.com/tednved/graduation-backend/pull/2) | `docs/handoffs/PM-03.md` | 无契约变化 | 2026-09-10 |
| DB-01 | `feat/DB-01-initial-schema` | 数据库 Agent（真实 MySQL 实测、PM-05 修订、PR #1 第二轮修订由主线程补做） | REVIEW | 2026-09-10 | [#1](https://github.com/tednved/graduation-backend/pull/1) | `docs/handoffs/DB-01.md` | Flyway V1～V5 / MySQL 8（V6 排序规则修订转 DB-02） | 2026-09-10 |
| DB-02 | `fix/DB-02-identifier-collations` | 数据库 Agent | REVIEW | 2026-09-10 | [#3](https://github.com/tednved/graduation-backend/pull/3) | `docs/handoffs/DB-02.md` | Flyway V6（6 个标识符列改 `utf8mb4_0900_bin`） | 2026-09-10 |

## 合并后更新要求

项目负责人合并 PR 后，将对应行改为 `DONE`，把 PR 填为可访问链接，并在“最近更新”中记录合并日期。已经结束的历史行只允许补充链接或纠正事实，不得删除。
