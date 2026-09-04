const util = require('../utils/util.js');

function checkLogin() {
  return wx.getStorageSync('userInfo') && wx.getStorageSync('token') ? Promise.resolve(true) : Promise.reject(
    new Error('请先登录'));
}

function checkLoginAndNav() {
  return util.requireLogin() ? Promise.resolve(true) : Promise.reject(new Error('请先登录'));
}

function checkUserAuth() {
  return util.getUserInfo().then(() => true);
}
module.exports = {
  checkLogin,
  checkLoginAndNav,
  checkUserAuth
};
