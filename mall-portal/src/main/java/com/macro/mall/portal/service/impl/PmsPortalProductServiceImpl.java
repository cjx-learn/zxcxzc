package com.macro.mall.portal.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.PmsPortalProductDetail;
import com.macro.mall.portal.domain.PmsProductCategoryNode;
import com.macro.mall.portal.service.PmsPortalProductService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 前台订单管理Service实现类
 * Created by macro on 2020/4/6.
 */
@Service
public class PmsPortalProductServiceImpl implements PmsPortalProductService {
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsProductCategoryMapper productCategoryMapper;
    @Autowired
    private PmsBrandMapper brandMapper;
    @Autowired
    private PmsProductAttributeMapper productAttributeMapper;
    @Autowired
    private PmsProductAttributeValueMapper productAttributeValueMapper;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private PmsProductLadderMapper productLadderMapper;
    @Autowired
    private PmsProductFullReductionMapper productFullReductionMapper;
    @Autowired
    private PortalProductDao portalProductDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public List<PmsProduct> search(String keyword, Long brandId, Long productCategoryId, Integer pageNum, Integer pageSize, Integer sort) {
        List<Long> searchCategoryIds = java.util.Collections.emptyList();
        if (StrUtil.isNotEmpty(keyword)) {
            searchCategoryIds = getSearchCategoryIds(keyword.trim());
        }
        PageHelper.startPage(pageNum, pageSize);
        PmsProductExample example = new PmsProductExample();
        if (StrUtil.isNotEmpty(keyword)) {
            String likeKeyword = "%" + keyword.trim() + "%";
            addSearchCriteria(example, brandId, productCategoryId, likeKeyword, "name");
            addSearchCriteria(example, brandId, productCategoryId, likeKeyword, "keywords");
            addSearchCriteria(example, brandId, productCategoryId, likeKeyword, "subTitle");
            addSearchCriteria(example, brandId, productCategoryId, likeKeyword, "brandName");
            addSearchCriteria(example, brandId, productCategoryId, likeKeyword, "productCategoryName");
            addCategorySearchCriteria(example, brandId, productCategoryId, searchCategoryIds);
        } else {
            PmsProductExample.Criteria criteria = example.createCriteria();
            applyVisibleCriteria(criteria, brandId, productCategoryId);
        }
        //1->????2->????3->???????4->??????
        if (sort == 0) {
            example.setOrderByClause("sort desc, id desc");
        } else if (sort == 1) {
            example.setOrderByClause("id desc");
        } else if (sort == 2) {
            example.setOrderByClause("sale desc");
        } else if (sort == 3) {
            example.setOrderByClause("price asc");
        } else if (sort == 4) {
            example.setOrderByClause("price desc");
        }
        return productMapper.selectByExample(example);
    }

    private void addSearchCriteria(PmsProductExample example, Long brandId, Long productCategoryId, String likeKeyword, String field) {
        PmsProductExample.Criteria criteria = example.or();
        applyVisibleCriteria(criteria, brandId, productCategoryId);
        if ("keywords".equals(field)) {
            criteria.andKeywordsLike(likeKeyword);
        } else if ("subTitle".equals(field)) {
            criteria.andSubTitleLike(likeKeyword);
        } else if ("brandName".equals(field)) {
            criteria.andBrandNameLike(likeKeyword);
        } else if ("productCategoryName".equals(field)) {
            criteria.andProductCategoryNameLike(likeKeyword);
        } else {
            criteria.andNameLike(likeKeyword);
        }
    }

    private void addCategorySearchCriteria(PmsProductExample example, Long brandId, Long productCategoryId, List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return;
        }
        PmsProductExample.Criteria criteria = example.or();
        criteria.andDeleteStatusEqualTo(0);
        criteria.andPublishStatusEqualTo(1);
        if (brandId != null) {
            criteria.andBrandIdEqualTo(brandId);
        }
        if (productCategoryId != null) {
            if (!categoryIds.contains(productCategoryId)) {
                return;
            }
            criteria.andProductCategoryIdEqualTo(productCategoryId);
        } else {
            criteria.andProductCategoryIdIn(categoryIds);
        }
    }

    private List<Long> getSearchCategoryIds(String keyword) {
        PmsProductCategoryExample example = new PmsProductCategoryExample();
        List<PmsProductCategory> allList = productCategoryMapper.selectByExample(example);
        java.util.Set<Long> categoryIds = allList.stream()
                .filter(item -> item.getName() != null && item.getName().contains(keyword))
                .map(PmsProductCategory::getId)
                .collect(Collectors.toSet());
        boolean changed;
        do {
            changed = false;
            for (PmsProductCategory item : allList) {
                if (categoryIds.contains(item.getParentId()) && categoryIds.add(item.getId())) {
                    changed = true;
                }
            }
        } while (changed);
        return new java.util.ArrayList<>(categoryIds);
    }

    private void applyVisibleCriteria(PmsProductExample.Criteria criteria, Long brandId, Long productCategoryId) {
        criteria.andDeleteStatusEqualTo(0);
        criteria.andPublishStatusEqualTo(1);
        if (brandId != null) {
            criteria.andBrandIdEqualTo(brandId);
        }
        if (productCategoryId != null) {
            criteria.andProductCategoryIdEqualTo(productCategoryId);
        }
    }


    @Override
    public List<PmsProductCategoryNode> categoryTreeList() {
        PmsProductCategoryExample example = new PmsProductCategoryExample();
        List<PmsProductCategory> allList = productCategoryMapper.selectByExample(example);
        List<PmsProductCategoryNode> result = allList.stream()
                .filter(item -> item.getParentId().equals(0L))
                .map(item -> covert(item, allList)).collect(Collectors.toList());
        return result;
    }

    @Override
    public PmsPortalProductDetail detail(Long id) {
        PmsPortalProductDetail result = new PmsPortalProductDetail();
        //获取商品信息
        PmsProduct product = productMapper.selectByPrimaryKey(id);
        result.setProduct(product);
        //获取品牌信息
        PmsBrand brand = brandMapper.selectByPrimaryKey(product.getBrandId());
        result.setBrand(brand);
        //获取商品属性信息
        PmsProductAttributeExample attributeExample = new PmsProductAttributeExample();
        attributeExample.createCriteria().andProductAttributeCategoryIdEqualTo(product.getProductAttributeCategoryId());
        List<PmsProductAttribute> productAttributeList = productAttributeMapper.selectByExample(attributeExample);
        result.setProductAttributeList(productAttributeList);
        //获取商品属性值信息
        if(CollUtil.isNotEmpty(productAttributeList)){
            List<Long> attributeIds = productAttributeList.stream().map(PmsProductAttribute::getId).collect(Collectors.toList());
            PmsProductAttributeValueExample attributeValueExample = new PmsProductAttributeValueExample();
            attributeValueExample.createCriteria().andProductIdEqualTo(product.getId())
                    .andProductAttributeIdIn(attributeIds);
            List<PmsProductAttributeValue> productAttributeValueList = productAttributeValueMapper.selectByExample(attributeValueExample);
            result.setProductAttributeValueList(productAttributeValueList);
        }
        //获取商品SKU库存信息
        PmsSkuStockExample skuExample = new PmsSkuStockExample();
        skuExample.createCriteria().andProductIdEqualTo(product.getId());
        List<PmsSkuStock> skuStockList = skuStockMapper.selectByExample(skuExample);
        result.setSkuStockList(skuStockList);
        if (CollUtil.isNotEmpty(skuStockList)) {
            product.setStock(skuStockList.stream()
                    .map(PmsSkuStock::getStock)
                    .filter(stock -> stock != null)
                    .reduce(0, Integer::sum));
        }
        result.setViewCount(countProductViews(product.getId()) + 1);
        //商品阶梯价格设置
        if(product.getPromotionType()==3){
            PmsProductLadderExample ladderExample = new PmsProductLadderExample();
            ladderExample.createCriteria().andProductIdEqualTo(product.getId());
            List<PmsProductLadder> productLadderList = productLadderMapper.selectByExample(ladderExample);
            result.setProductLadderList(productLadderList);
        }
        //商品满减价格设置
        if(product.getPromotionType()==4){
            PmsProductFullReductionExample fullReductionExample = new PmsProductFullReductionExample();
            fullReductionExample.createCriteria().andProductIdEqualTo(product.getId());
            List<PmsProductFullReduction> productFullReductionList = productFullReductionMapper.selectByExample(fullReductionExample);
            result.setProductFullReductionList(productFullReductionList);
        }
        //商品可用优惠券
        result.setCouponList(portalProductDao.getAvailableCouponList(product.getId(),product.getProductCategoryId()));
        return result;
    }

    private long countProductViews(Long productId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_behavior_event WHERE product_id = ? AND event_type = 'view'",
                Long.class,
                productId);
        return count == null ? 0L : count;
    }


    /**
     * 初始对象转化为节点对象
     */
    private PmsProductCategoryNode covert(PmsProductCategory item, List<PmsProductCategory> allList) {
        PmsProductCategoryNode node = new PmsProductCategoryNode();
        BeanUtils.copyProperties(item, node);
        List<PmsProductCategoryNode> children = allList.stream()
                .filter(subItem -> subItem.getParentId().equals(item.getId()))
                .map(subItem -> covert(subItem, allList)).collect(Collectors.toList());
        node.setChildren(children);
        return node;
    }
}
