# NfcAimeReaderDLL
使用.NET9.0编写支持WebSocket的AimeIO
## 😋如何使用
1. 将`NfcAimeReaderDLL.dll`放到`AMDaemon.exe`同一目录下
2. 编辑`segatools.ini`
```ini
[aimeio]
path=NfcAimeReaderDLL.dll

;serverAddress - 监听地址默认0.0.0.0
;serverPort - 监听端口默认6071
;serverAddress=0.0.0.0
;serverPort=6071
```
3. 运行游戏检查amdaemon控制台是否有`NfcAimeReader`启动的消息
4. 使用客户端连接，刷卡进行游戏

## 🍭Future
 - 加密支持（当前版本不支持解密加密数据包）
 - Access Code映射(当然这是客户端那边的事，不过这边得适配)

## 🔨构建
需要Visual Studio 2022（或生成工具）与使用 C++ 和 .NET 桌面开发工作负载（.NET9.0）的桌面开发工作负载，包括 MSVC x64/x86 编译器和最新的 Windows 10/11 SDK。

如果使用Visual Studio 2022：

必须使用`发布项目`来构建DLL且保证`配置` 为`Release x64`，`部署模式`为`独立`，否则会导致游戏无法运行

如果使用命令行:
```cmd
dotnet publish -c Release -r win-x64 -p:Version=1.0 /NfcAimeReaderDLL.csproj
```
## 🥰致谢
本项目参考了[ppc/AMNet](https://gitea.tendokyu.moe/ppc/amnet)感谢ppc大佬提供的优质开源项目

## 📌License
NfcAimeReaderDLL衍生自[AMNet](https://gitea.tendokyu.moe/ppc/amnet)，使用AGPLv3或其更高版本获得许可，更多请参阅[license.md](license.md)