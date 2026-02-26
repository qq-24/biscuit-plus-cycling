# Biscuit 🚴

一款 Android 骑行码表应用，支持 GPS 轨迹记录、ANT+ 传感器、自动暂停、骑行历史管理和 GPX 导出。

## 功能特性

- GPS 实时速度与轨迹记录
- ANT+ 传感器支持（速度/踏频）
- 自动暂停 / 手动暂停
- 骑行数据仪表盘（速度、距离、时间、卡路里）
- 骑行历史记录与详情查看
- GPX 轨迹导出
- 天地图 / OSM 地图显示
- 低速提醒振动反馈
- 前台服务持续记录，支持后台运行

## 技术栈

- Kotlin + Android SDK (minSdk 26, targetSdk 35)
- Room 数据库
- osmdroid 地图
- ANT+ Plugin Library
- MPAndroidChart 图表
- JUnit 5 + Kotest 属性测试

## 构建

```bash
# 克隆仓库
git clone https://github.com/qq-24/biscuit-plus-cycling.git
cd biscuit-plus-cycling

# 配置天地图 Token（可选，用于天地图图层）
echo "TIANDITU_TOKEN=your_token_here" >> local.properties

# 构建 debug 版本
./gradlew assembleRealDebug
```

## 项目结构

```
app/src/main/java/net/telent/biscuit/
├── BikeActivity.kt          # 主 Activity
├── BiscuitService.kt        # 前台服务，核心骑行记录逻辑
├── AutoPauseLogic.kt        # 自动暂停判定
├── PauseAwareDistanceTracker.kt  # 暂停感知的距离追踪
├── RideStatsCalculator.kt   # 骑行统计计算
├── Sensor.kt                # GPS / ANT+ 传感器抽象
├── GpxSerializer.kt         # GPX 文件导出
├── ui/
│   ├── home/                # 主仪表盘
│   ├── history/             # 骑行历史
│   ├── track/               # 地图轨迹
│   ├── sensors/             # 传感器管理
│   └── settings/            # 设置
```

## License

MIT
