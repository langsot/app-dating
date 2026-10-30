// Заглушка telegram-web-app.js: подписанные initData и имитация системной кнопки MainButton
// (как в Telegram, при показе кнопки область веб-приложения уменьшается на ее высоту).
export const telegramStub = (data, name) => `
(function(){
  var cb=null, bar=null;
  function ensure(){ if(!bar){ bar=document.createElement('div'); bar.id='tg-main-button';
    bar.style.cssText='position:fixed;left:0;right:0;bottom:0;height:52px;background:#2481cc;color:#fff;display:none;align-items:center;justify-content:center;font:600 16px sans-serif;z-index:9999;cursor:pointer';
    bar.onclick=function(){ cb && cb(); }; document.body.appendChild(bar);} return bar; }
  window.__tgLog=[];
  window.Telegram={WebApp:{
    initData:${JSON.stringify(data)},
    initDataUnsafe:{user:{id:0,first_name:${JSON.stringify(name)}}},
    themeParams:{bg_color:'#ffffff',text_color:'#000000',hint_color:'#999999',button_color:'#2481cc',button_text_color:'#ffffff',secondary_bg_color:'#f1f1f4'},
    colorScheme:'light', ready:function(){}, expand:function(){}, close:function(){},
    HapticFeedback:{impactOccurred:function(s){window.__tgLog.push('haptic:'+s)},notificationOccurred:function(s){window.__tgLog.push('notify:'+s)}},
    openTelegramLink:function(u){window.__tgLog.push('open:'+u)},
    MainButton:{ setText:function(t){ensure().textContent=t.toUpperCase()}, show:function(){ensure().style.display='flex'; var r=document.getElementById('root'); if(r) r.style.height='calc(100% - 52px)'},
      hide:function(){ensure().style.display='none'; var r=document.getElementById('root'); if(r) r.style.height=''}, enable:function(){}, disable:function(){},
      showProgress:function(){}, hideProgress:function(){}, onClick:function(f){cb=f}, offClick:function(){cb=null} }
  }};
})();`;
