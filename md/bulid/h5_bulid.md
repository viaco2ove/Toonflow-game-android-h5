cd {Toonflow-game-web}
yarn build


## 2. 复制到 Android Assets

```bash
# 删除旧资源
rm -rf app/src/main/assets/dist

# 复制新构建
cp -r {Toonflow-game-web}/dist app/src/main/assets/
```
效果是
app/src/main/assets/dist/index.html