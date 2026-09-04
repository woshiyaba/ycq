const util = require('../../../utils/util.js');
const workTypes = ['FULL_TIME', 'PART_TIME'],
  salaryUnits = ['MONTH', 'DAY', 'HOUR', 'ONCE', 'NEGOTIABLE'],
  settlements = ['MONTHLY', 'WEEKLY', 'DAILY', 'ON_COMPLETION'];
Page({
  data: {
    id: '',
    kind: 'COMMUNITY',
    form: {
      title: '',
      body: '',
      images: [],
      region: '运城市',
      job: {
        workType: 'FULL_TIME',
        industry: '餐饮服务',
        salary: '',
        salaryUnit: 'MONTH',
        settlement: 'MONTHLY',
        address: '',
        headcount: 1,
        company: '',
        requirements: '',
        benefits: [],
        contactName: '',
        contactPhone: ''
      }
    },
    benefitsText: '',
    workNames: ['全职', '兼职'],
    salaryNames: ['元 / 月', '元 / 天', '元 / 小时', '元 / 次', '面议'],
    settlementNames: ['月结', '周结', '日结', '完工结算'],
    industries: ['餐饮服务', '销售零售', '物流配送', '家政保洁', '生产制造', '教育培训', '文员行政', '技术设计', '其他'],
    workIndex: 0,
    salaryIndex: 0,
    settlementIndex: 0,
    industryIndex: 0,
    busy: false,
    uploading: false,
    error: ''
  },
  async onLoad(options) {
    if (!util.requireLogin()) return;
    this.setData({
      id: options.id || '',
      kind: options.kind || 'COMMUNITY'
    });
    if (options.id) {
      try {
        const data = await util.api('/post/entries/' + options.id);
        this.setData({
          kind: data.kind,
          form: {
            title: data.title,
            body: data.body,
            images: data.images || [],
            region: data.region,
            job: data.job || this.data.form.job
          },
          benefitsText: (data.job && data.job.benefits || []).join('、')
        });
        if (data.job) this.setData({
          workIndex: Math.max(0, workTypes.indexOf(data.job.workType)),
          salaryIndex: Math.max(0, salaryUnits.indexOf(data.job.salaryUnit)),
          settlementIndex: Math.max(0, settlements.indexOf(data.job.settlement)),
          industryIndex: Math.max(0, this.data.industries.indexOf(data.job.industry))
        });
      } catch (error) {
        this.setData({
          error: error.message
        });
      }
    }
    wx.setNavigationBarTitle({
      title: (options.id ? '编辑' : '发布') + (this.data.kind === 'RECRUITMENT' ? '招聘' : '动态')
    });
  },
  input(e) {
    this.setData({
      ['form.' + e.currentTarget.dataset.field]: e.detail.value
    });
  },
  benefits(e) {
    this.setData({
      benefitsText: e.detail.value
    });
  },
  pick(e) {
    const field = e.currentTarget.dataset.field;
    const index = Number(e.detail.value);
    const values = {
      workType: workTypes,
      salaryUnit: salaryUnits,
      settlement: settlements,
      industry: this.data.industries
    };
    const indexes = {
      workType: 'workIndex',
      salaryUnit: 'salaryIndex',
      settlement: 'settlementIndex',
      industry: 'industryIndex'
    };
    this.setData({
      ['form.job.' + field]: values[field][index],
      [indexes[field]]: index
    });
  },
  async addImages() {
    if (this.data.uploading) return;
    this.setData({
      uploading: true
    });
    try {
      const urls = await util.uploadImages(9 - this.data.form.images.length);
      this.setData({
        'form.images': this.data.form.images.concat(urls)
      });
    } catch (error) {
      if (!/cancel/.test(error.errMsg || '')) util.showErrorToast(error);
    } finally {
      this.setData({
        uploading: false
      });
    }
  },
  removeImage(e) {
    const images = this.data.form.images.slice();
    images.splice(e.currentTarget.dataset.index, 1);
    this.setData({
      'form.images': images
    });
  },
  async submit() {
    if (this.data.busy || this.data.uploading || !util.requireLogin()) return;
    const form = JSON.parse(JSON.stringify(this.data.form));
    form.kind = this.data.kind;
    form.title = form.title.trim();
    form.body = form.body.trim();
    if (!form.title || !form.body || !form.region.trim()) {
      util.showErrorToast('请填写标题、内容和地区');
      return;
    }
    if (form.kind === 'RECRUITMENT') {
      const job = form.job;
      job.salary = job.salaryUnit === 'NEGOTIABLE' ? null : Number(job.salary);
      job.headcount = Number(job.headcount);
      job.benefits = this.data.benefitsText.split(/[、,，]/).map(s => s.trim()).filter(Boolean);
      if (!job.company.trim() || !job.address.trim() || !job.requirements.trim() || !job.contactName
        .trim() || !/^\+?[0-9][0-9 -]{5,28}$/.test(job.contactPhone.trim())) {
        util.showErrorToast('请填写单位、地址、岗位要求、联系人及有效电话');
        return;
      }
      if (!Number.isInteger(job.headcount) || job.headcount < 1 || job.headcount > 100000 || (job
          .salaryUnit !== 'NEGOTIABLE' && (
            !Number.isFinite(job.salary) || job.salary <= 0 || job.salary > 99999999.99 || !
            /^\d+(\.\d{1,2})?$/.test(String(job.salary))))) {
        util.showErrorToast('请填写有效薪资和招聘人数');
        return;
      }
    } else delete form.job;
    this.setData({
      busy: true
    });
    try {
      const data = await util.api('/post/entries' + (this.data.id ? '/' + this.data.id : ''), form, this
        .data.id ? 'PUT' : 'POST');
      const id = this.data.id || (data && data.id) || data;
      wx.redirectTo({
        url: '/pages/content/detail/detail?id=' + id
      });
    } catch (error) {
      util.showErrorToast(error);
    } finally {
      this.setData({
        busy: false
      });
    }
  }
});
