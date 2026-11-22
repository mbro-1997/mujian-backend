const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const { execFileSync } = require('child_process');
const automator = require('miniprogram-automator');

// 优先复用上次由原工程 CLI 建立的后台连接。运行时标记随重编译消失。
async function connect(cli, project, keyFile, port) {
  const file = path.join(path.dirname(keyFile), 'simulator.json');
  if (fs.existsSync(file)) {
    let previous;
    try {
      const saved = JSON.parse(fs.readFileSync(file, 'utf8'));
      if (!Number.isInteger(saved.port) || !/^[0-9a-f]{32}$/.test(saved.marker)) throw Error();
      previous = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:' + saved.port });
      const marker = await previous.evaluate(() => getApp().globalData.localDebugRuntime);
      if (marker === saved.marker) return previous;
    } catch {
      // 服务端口关闭或模拟器已重启，由项目路径重新定位。
    }
    previous?.disconnect();
  }
  const out = execFileSync(cli, ['auto', '--project', project, '--auto-port', port],
    { encoding: 'utf8', stdio: ['ignore','pipe','inherit'], timeout: 60000 });
  const actual = JSON.parse(out.slice(out.lastIndexOf('{'))).autoPort;
  const mini = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:' + actual });
  const marker = crypto.randomBytes(16).toString('hex');
  await mini.evaluate(value => { getApp().globalData.localDebugRuntime = value; }, marker);
  fs.writeFileSync(file, JSON.stringify({ port: actual, marker }), { mode: 0o600 });
  return mini;
}

async function main() {
  const [cli, project, keyFile, port, role] = process.argv.slice(2);
  const mini = await connect(cli, project, keyFile, port);
  try {
    const info = await mini.evaluate(() => ({
      platform: wx.getDeviceInfo().platform,
      app: wx.getAccountInfoSync().miniProgram,
      origin: require('config/environment.js').baseUrl,
    }));
    const addresses = Object.values(os.networkInterfaces()).flat().map(a => a.address);
    const origin = new URL(info.origin);
    if (info.app.envVersion !== 'develop' || info.platform !== 'devtools' ||
        info.app.appId !== JSON.parse(fs.readFileSync(path.join(project,'project.config.json'))).appid ||
        !addresses.includes(origin.hostname) || origin.protocol !== 'http:' || origin.port !== String(require(path.join(project, 'config/development.js')).apiPort) ||
        origin.origin !== info.origin) throw Error('请选择连接本机开发服务的原小程序。');
    if (role === "developer-code") {
      const code = await mini.evaluate(() => new Promise((resolve, reject) => wx.login({
        success: r => r.code ? resolve(r.code) : reject(new Error("开发者工具未提供微信登录码")),
        fail: () => reject(new Error("开发者工具微信登录失败")),
      })));
      // stdout 仅连接本机 Python 的私有管道，不落盘。
      process.stdout.write(code);
      return;
    }
    const pair = { origin: info.origin, secret: fs.readFileSync(keyFile,'utf8').trim() };
    await mini.evaluate(pair => { wx.setStorageSync('mj.localDebugPair', pair); }, pair);
    const user = await mini.evaluate(role => require('services/local-debug-accounts.js').switchAccount(role), role);
    for (let n = 0; n < 35; n++) {
      const home = await mini.evaluate(() => {
        const p = getCurrentPages().pop();
        if (!p || p.route !== 'pages/home/index' || !p.data.rootReady) return null;
        return { user: p.data.factoryContext?.userId, role: p.data.factoryContext?.roleCode,
          factory: p.data.factoryProfile?.id, tabs: p.getTabBar().data.items.map(i=>i.text) };
      });
      if (home?.user === user && home.role === role && (role !== 'supplier' || home.factory)) {
        console.log(user + '  ' + home.tabs.join(' / ') + (home.factory ? '  工厂 ' + home.factory : ''));
        return;
      }
      await new Promise(r=>setTimeout(r,300));
    }
    throw Error('会话已载入，首页未能确认当前账号。');
  } finally { mini.disconnect(); }
}
main().catch(e=>{ console.error(e.message); process.exitCode=1; });
