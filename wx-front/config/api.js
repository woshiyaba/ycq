// 开发者工具控制台可执行：wx.setStorageSync('apiRoot', 'http://127.0.0.1:8080/')
// 删除 apiRoot 缓存即可恢复默认服务器。
const DEFAULT_ROOT = 'http://159.75.222.113:8080/';

function root() {
  const value = wx.getStorageSync('apiRoot') || DEFAULT_ROOT;
  return value.replace(/\/+$/, '') + '/';
}
const paths = {
  IndexUrl: 'index/index',
  IndexMore: 'index/more',
  CatalogList: 'catalog/index',
  CatalogCurrent: 'catalog',
  GoodsCategory: 'goods/category/index',
  GoodsList: 'goods/category',
  GoodsDetail: 'goods/detail',
  GoodsRelated: 'goods/related',
  CommentPost: 'goods/comment/post',
  SearchIndex: 'search/index',
  SearchResult: 'search/result',
  SearchClearHistory: 'search/clearhistory',
  GoodsPost: 'post/post',
  GoodsDelete: 'post/delete',
  RegionList: 'post/region',
  PostCateList: 'post/category',
  ImageUpload: 'upload/image',
  CollectList: 'goodsUser/collect',
  CollectAddOrDelete: 'goodsUser/collect/addordelete',
  PostedList: 'goodsUser/posted',
  BoughtList: 'goodsUser/bought',
  SoldList: 'goodsUser/sold',
  UserPage: 'goodsUser/user',
  UserPageMore: 'goodsUser/user/more',
  GoodsWant: 'goodsUser/want',
  AuthLoginByWeixin: 'auth/loginByWeixin',
  ChatIndex: 'chat/index',
  ChatForm: 'chat/form',
  ChatFlushUnread: 'chat/flushUnread'
};
const api = {
  root
};
Object.keys(paths).forEach(key => Object.defineProperty(api, key, {
  enumerable: true,
  get: () => root() + paths[key]
}));
Object.defineProperty(api, 'ChatWs', {
  enumerable: true,
  get: () => root().replace(/^http/, 'ws') + 'ws'
});
module.exports = api;
