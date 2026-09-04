const util = require('./util.js');
const salaryUnits = {
  MONTH: '月',
  DAY: '天',
  HOUR: '小时',
  ONCE: '次',
  NEGOTIABLE: '面议'
};

function decorate(item) {
  if (item) {
    ['createdAt', 'updatedAt', 'visitedAt'].forEach(key => {
      if (item[key]) item[key] = util.displayTime(item[key]);
    });
  }
  if (item && item.job) item.salaryLabel = item.job.salaryUnit === 'NEGOTIABLE' ? '薪资面议' : (item.job.salary ||
    0) + '元/' + (salaryUnits[item.job.salaryUnit] || '月');
  return item;
}

function items(data) {
  return (Array.isArray(data) ? data : data.items || []).map(decorate);
}
module.exports = {
  decorate,
  items,
  salaryUnits
};
