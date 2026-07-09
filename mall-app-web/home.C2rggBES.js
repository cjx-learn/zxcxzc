import{h as t,u as o}from"./http.94CetqdY.js";

const e=()=>t({method:"GET",url:"/home/content"});
const a=e=>t({method:"GET",url:`/home/productCateList/${e}`});
const d=e=>t({method:"GET",url:"/home/newProductList",data:e});
const m=e=>t({method:"GET",url:"/home/hotProductList",data:e});

const n=e=>({
  id:e.productId||e.id,
  categoryId:e.categoryId||e.productCategoryId,
  pic:e.productPic||e.pic,
  name:e.productName||e.name,
  subTitle:e.reason?"根据你的浏览和购买偏好推荐":e.subTitle||"为你推荐",
  price:null!=e.productPrice?e.productPrice:e.price,
  recommendType:e.recommendType,
  recommendScore:e.recommendScore
});

const r=()=>{
  try{
    const e=o().memberInfo;
    return e&&(e.id||e.memberId||e.userId)
  }catch(e){
    return null
  }
};

const trackBehavior=e=>{
  const a=e&&Number(e.id||e.productId);
  if(!a)return Promise.resolve();
  const d=Number(r()||1);
  const m=window.location.origin;
  let n="h5-session";
  try{
    n=localStorage.getItem("mall_h5_session_id");
    n||(n=`h5-${Date.now()}-${Math.random().toString(16).slice(2)}`,localStorage.setItem("mall_h5_session_id",n))
  }catch(e){}
  return t({method:"POST",url:`${m}/mall-analytics/analytics/event`,data:{userId:d,productId:a,categoryId:e.categoryId,eventType:"view",sessionId:n,sourcePage:"h5-home",deviceType:"h5"}})
};

const s=e=>{
  const a=Number((null==e?void 0:e.pageNum)||1);
  const d=Number((null==e?void 0:e.pageSize)||4);
  return{pageNum:a,pageSize:d,limit:a*d,start:(a-1)*d}
};

const c=async e=>{
  const{pageSize:a,limit:d,start:m}=s(e);
  const c=window.location.origin;
  const i=r();
  if(i){
    const e=await t({method:"GET",url:`${c}/mall-recommend/recommend/user/${i}`,params:{type:"model_deepfm",limit:d}});
    const r=(e.data||[]).map(n);
    if(r.length)return{...e,data:r.slice(m,m+a)}
  }
  const l=await t({method:"GET",url:`${c}/mall-recommend/recommend/hot`,params:{limit:d}});
  return{...l,data:(l.data||[]).map(n).slice(m,m+a)}
};

export{e as a,a as b,m as c,d,c as g,trackBehavior as t};
