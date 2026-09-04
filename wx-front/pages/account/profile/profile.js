const util = require('../../../utils/util.js');
const websocket = require('../../../services/websocket.js');
Page({
  data: {
    profile: {
      nickName: '',
      avatarUrl: '',
      bio: '',
      gender: 0,
      region: ''
    },
    genderNames: ['暂不填写', '男', '女'],
    busy: false,
    uploading: false,
    error: ''
  },
  async onLoad() {
    if (!util.requireLogin()) return;
    try {
      this.setData({
        profile: await util.api('/goodsUser/profile')
      });
    } catch (error) {
      this.setData({
        error: error.message
      });
    }
  },
  input(e) {
    this.setData({
      ['profile.' + e.currentTarget.dataset.field]: e.detail.value
    });
  },
  gender(e) {
    this.setData({
      'profile.gender': Number(e.detail.value)
    });
  },
  async avatar() {
    if (this.data.uploading) return;
    this.setData({
      uploading: true
    });
    try {
      const images = await util.uploadImages(1);
      this.setData({
        'profile.avatarUrl': images[0]
      });
    } catch (error) {
      if (!/cancel/.test(error.errMsg || '')) util.showErrorToast(error);
    } finally {
      this.setData({
        uploading: false
      });
    }
  },
  async save() {
    if (this.data.busy || this.data.uploading) return;
    if (!this.data.profile.nickName.trim()) {
      util.showErrorToast('请填写昵称');
      return;
    }
    this.setData({
      busy: true
    });
    try {
      const p = this.data.profile;
      const profile = await util.api('/goodsUser/profile', {
        nickName: p.nickName.trim(),
        avatarUrl: p.avatarUrl,
        bio: p.bio || '',
        gender: Number(p.gender) || 0,
        region: p.region || ''
      }, 'PUT');
      wx.setStorageSync('userInfo', profile);
      getApp().globalData.userInfo = profile;
      wx.showToast({
        title: '保存成功'
      });
    } catch (error) {
      util.showErrorToast(error);
    } finally {
      this.setData({
        busy: false
      });
    }
  },
  async logout() {
    if (!await util.confirm('确定退出当前账号？')) return;
    websocket.wsClose();
    websocket.setBadge(0);
    wx.removeStorageSync('token');
    wx.removeStorageSync('userInfo');
    getApp().globalData.userInfo = {
      openId: '',
      nickName: 'Hi，欢迎来到运城圈',
      avatarUrl: ''
    };
    getApp().globalData.token = '';
    wx.switchTab({
      url: '/pages/ucenter/index/index'
    });
  }
});
