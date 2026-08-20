# 论文截图与演示数据

本目录保存由一次性隔离数据库生成的论文图片。演示数据覆盖幸福里、和苑、清河、东湖 4 个社区，以及 4 个网格、4 位居民、16 个事件和 15 个任务；事件与任务分别保留待受理、待接单、处理中、待复核、已完成、已驳回和已取消等状态，便于展示完整治理闭环。

已整理好的正文见[《第5章 系统实现与第6章 系统测试》](chapter-system-implementation-and-testing.md)。其中包含8张图片的插入位置、实现说明、测试环境、测试用例表、结果分析与测试边界；复制到学校论文模板后再统一调整章节号和排版。

## 图片目录

| 文件 | 建议图题 | 建议题注 |
|---|---|---|
| [01-login.png](screenshots/01-login.png) | 系统登录界面 | 展示平台身份认证入口与系统定位。 |
| [02-dashboard.png](screenshots/02-dashboard.png) | 社区治理概览 | 展示网格、居民、重点人群及事件处置阶段统计。 |
| [03-user-permissions.png](screenshots/03-user-permissions.png) | 用户与角色管理 | 展示系统管理员、社区工作人员和网格员账号。 |
| [04-grid-responsibility-map.png](screenshots/04-grid-responsibility-map.png) | 网格空间地图 | 真实坐标点位与待定位网格同屏呈现。 |
| [05-resident-cards.png](screenshots/05-resident-cards.png) | 居民档案卡片 | 展示居民、家庭户、重点人群标签与所属网格。 |
| [06-event-flow.png](screenshots/06-event-flow.png) | 治理事件流程追踪 | 展示待受理、处理中、待复核和已办结等阶段。 |
| [07-task-board.png](screenshots/07-task-board.png) | 网格任务执行看板 | 展示任务在接单、处置、复核和完成阶段的分布。 |
| [08-mobile-events.png](screenshots/08-mobile-events.png) | 移动端治理事件 | 展示窄屏下概览折叠、筛选与事件卡片布局。 |

桌面端图片为 1920×1080，移动端图片为 390×844。机器可读的生成时间、数据量、视口和运行错误计数见 [manifest.json](screenshots/manifest.json)。

## 一键重拍

先完成一次 `scripts/validation-pipeline.sh`，确保后端 JAR、前端依赖和本机工具链可用。然后在项目根目录执行：

```bash
export THESIS_DB_ADMIN_USERNAME='root'
printf 'MySQL password: '
IFS= read -rs THESIS_DB_ADMIN_PASSWORD
printf '\n'
export THESIS_DB_ADMIN_PASSWORD

scripts/generate-thesis-figures.sh

unset THESIS_DB_ADMIN_PASSWORD
```

本机无密码测试实例可不设置 `THESIS_DB_ADMIN_PASSWORD`。脚本会生成临时管理员密码和数据加密密钥，在随机本地端口启动本轮前后端，装载演示数据并重拍图片；这些凭据不会输出或写入项目。无论成功、失败还是收到中断信号，脚本都只停止本轮进程，并删除名称通过 `community_governance_thesis_` 前缀校验的本轮数据库。

如需单独使用两个底层入口：[seed-demo.mjs](../../scripts/seed-demo.mjs) 负责向已经启动的全新隔离环境装载数据，[thesis-screenshots.py](../../scripts/thesis-screenshots.py) 负责对已经启动的前端拍摄并校验图片。前者必须显式设置 `DEMO_CONFIRM_ISOLATED=YES`，禁止指向已有业务库。
