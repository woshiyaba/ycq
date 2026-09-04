// Run together with smoke.js; exercises request ordering and real feed pagination.
const assert = require('assert');
const fs = require('fs');
const vm = require('vm');
const app = require('../app.json');

module.exports = async function() {
  const pending = [];
  const navigations = [];
  let login = false;
  const context = {
    module: {
      exports: {}
    },
    require: name => name === '../app.json' ? app : {
      api: (url, query) => new Promise((resolve, reject) => pending.push({
        url,
        query,
        resolve,
        reject
      })),
      requireLogin: () => login,
      displayTime: value => value || ''
    },
    wx: {
      getWindowInfo: () => ({
        windowWidth: 393,
        statusBarHeight: 24
      }),
      getMenuButtonBoundingClientRect: () => ({
        bottom: 56
      }),
      getStorageSync: () => ({}),
      stopPullDownRefresh() {},
      navigateTo: options => navigations.push(options.url),
      switchTab: options => navigations.push(options.url),
      showToast() {}
    }
  };
  vm.runInNewContext(fs.readFileSync(require.resolve('../utils/feed.js'), 'utf8'), context);
  const feed = context.module.exports;
  const page = feed.page('HOME');
  page.data = JSON.parse(JSON.stringify(page.data));
  page.setData = values => Object.assign(page.data, values);
  const event = key => ({
    currentTarget: {
      dataset: {
        key
      }
    }
  });
  const item = (id, kind = 'GOODS') => ({
    id,
    kind,
    title: '真实接口商品',
    price: kind === 'GOODS' ? 99 : null
  });
  const result = items => ({
    items,
    total: 4,
    page: 1,
    hasMore: true,
    banners: [{
      id: 1
    }]
  });
  const tick = () => new Promise(resolve => setImmediate(resolve));

  page.onLoad();
  assert.strictEqual(page.data.chromeHeight, 64, 'custom header reserves the system capsule');
  assert.strictEqual(pending[0].url, '/index/feed');
  assert.strictEqual(pending[0].query.scene, 'HOME');
  page.switchChannel(event('NEW'));
  pending[0].resolve(result([item(99)]));
  await tick();
  assert.strictEqual(page.data.columns[0].length, 0,
    'late previous-channel response cannot replace current results');
  assert.strictEqual(page.data.loading, true,
    'late previous-channel finally cannot clear current loading');
  pending[1].resolve(result([item(1), item(2, 'COMMUNITY')]));
  await tick();
  assert.strictEqual(page.data.page, 2);
  assert.strictEqual(page.data.banners.length, 1);
  const secondPage = page.load();
  page.load();
  assert.strictEqual(pending.length, 3, 'repeated bottom events must not duplicate page requests');
  assert.strictEqual(pending[2].query.page, 2);
  pending[2].resolve({
    items: [item(1), item(1, 'COMMUNITY'), item(3)],
    total: 4,
    hasMore: false
  });
  await secondPage;
  const records = page.data.columns.flat();
  assert.strictEqual(records.length, 4, 'pagination deduplicates by kind and ID');
  assert(records.some(value => value.feedKey === 'COMMUNITY-1'));
  assert.strictEqual(page.data.banners.length, 1, 'later pages preserve first-page banners');
  page.load();
  assert.strictEqual(pending.length, 3, 'end-of-feed does not fetch again');

  page.switchChannel(event('FOLLOWING'));
  assert.strictEqual(page.data.channel, 'NEW', 'following is login protected');
  login = true;
  page.switchChannel(event('FOLLOWING'));
  pending[3].resolve(result([{
    ...item(4),
    author: {
      openId: 'seller'
    },
    images: ['image-a', 'image-b'],
    region: '运城',
    followerCount: 2
  }]));
  await tick();
  assert.strictEqual(page.data.following[0].images.length, 2);
  assert.strictEqual(page.data.following[0].priceLabel, '99');
  page.openAuthor({
    currentTarget: {
      dataset: {
        id: 'seller'
      }
    }
  });
  assert.strictEqual(navigations.pop(), '/pages/user/user?userId=seller');
  page.chooseCategory(event('jobs'));
  assert.strictEqual(navigations.pop(), '/pages/recruit/index/index');
  assert.strictEqual(pending.length, 4);
  page.chooseCategory(event('mobile'));
  assert.strictEqual(pending[4].query.categoryKey, 'mobile');
  assert.strictEqual(pending[4].query.channel, 'RECOMMENDED');
  pending[4].reject(new Error('网络连接失败'));
  await tick();
  assert.strictEqual(page.data.error, '网络连接失败');
  assert.strictEqual(page.data.page, 1, 'failed page remains retryable');
  const retry = page.load();
  pending[5].resolve({
    items: [],
    total: 0,
    hasMore: false
  });
  await retry;
  assert.strictEqual(page.data.error, '');
  assert.strictEqual(page.data.hasMore, false);
  page.data.keyword = '  手机  ';
  page.search();
  assert.strictEqual(pending[6].query.keyword, '手机');
  page.onUnload();
  pending[6].resolve(result([item(100)]));
  await tick();
  assert.strictEqual(page.data.columns[0].length, 0, 'unloaded page discards in-flight results');

  const columns = feed.distribute([item(1), item(2, 'COMMUNITY'), item(3, 'COMMUNITY')], 181);
  assert.strictEqual(columns[0].length, 1);
  assert.strictEqual(columns[1].length, 2, 'masonry appends to the shorter column, not equal grid rows');
  const featured = feed.page('FEATURED');
  assert.strictEqual(featured.data.categories.length, 10);
  assert.strictEqual(featured.data.categories[1].key, 'office');
  const wxml = fs.readFileSync(require.resolve('../templates/feed.wxml'), 'utf8');
  for (const match of wxml.matchAll(/(?:bind|catch)(?:tap|input|confirm)="([A-Za-z0-9_]+)"/g)) {
    assert.strictEqual(typeof page[match[1]], 'function', 'feed missing handler ' + match[1]);
  }
};
