package com.imooc.article.service.impl;

import com.github.pagehelper.PageHelper;
import com.imooc.api.config.RabbitMQConfig;
import com.imooc.api.config.RabbitMQDelayConfig;
import com.imooc.api.service.BaseService;
import com.imooc.article.mapper.ArticleMapper;
import com.imooc.article.service.ArticleService;
import com.imooc.enums.ArticleAppointType;
import com.imooc.enums.ArticleReviewLevel;
import com.imooc.enums.ArticleReviewStatus;
import com.imooc.enums.YesOrNo;
import com.imooc.exception.GraceException;
import com.imooc.grace.result.GraceJSONResult;
import com.imooc.grace.result.ResponseStatusEnum;
import com.imooc.pojo.Article;
import com.imooc.pojo.Category;
import com.imooc.pojo.bo.NewArticleBO;
import com.imooc.pojo.eo.ArticleEO;
import com.imooc.pojo.vo.ArticleDetailVO;
import com.imooc.utils.DateUtil;
import com.imooc.utils.JsonUtils;
import com.imooc.utils.PagedGridResult;
import com.imooc.article.controller.ArticlePortalController;
import com.imooc.utils.extend.AliTextReviewUtils;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.gridfs.GridFS;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.checkerframework.checker.units.qual.A;
import org.n3r.idworker.Sid;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.client.RestTemplate;
import tk.mybatis.mapper.entity.Example;

import java.io.File;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArticleServiceImpl extends BaseService implements ArticleService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private Sid sid;

    @Autowired
    private GridFSBucket gridFSBucket;

    @Autowired
    private ElasticsearchTemplate esTemplate;

    @Autowired
    private AliTextReviewUtils aliTextReviewUtils;

    private static LocalDateTime epoch=LocalDateTime.of(2020,6,1,0,0,0);

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void createArticle(NewArticleBO newArticleBO, Category category) {

        String articleId = sid.nextShort();

        Article article = new Article();
        BeanUtils.copyProperties(newArticleBO, article);

        article.setId(articleId);
        article.setCategoryId(category.getId());
        article.setArticleStatus(ArticleReviewStatus.REVIEWING.type);
        article.setCommentCounts(0);
        article.setReadCounts(0);

        article.setIsDelete(YesOrNo.NO.type);
        article.setCreateTime(new Date());
        article.setUpdateTime(new Date());

        if (article.getIsAppoint().equals(ArticleAppointType.TIMING.type)) {
            article.setPublishTime(newArticleBO.getPublishTime());
        } else if (article.getIsAppoint().equals(ArticleAppointType.IMMEDIATELY.type)) {
            article.setPublishTime(new Date());
        }

        int res = articleMapper.insert(article);
        if (res != 1) {
            GraceException.display(ResponseStatusEnum.ARTICLE_CREATE_ERROR);
        }


        // ?????????????????????mq????????????????????????????????????????????????????????????????????????????????????
        if (article.getIsAppoint().equals(ArticleAppointType.TIMING.type)) {

            Date endDate = newArticleBO.getPublishTime();
            Date startDate = new Date();

            System.out.println("????????????"+DateUtil.timeBetween(startDate, endDate));

            // FIXME: ???????????????????????????10s
            int delayTimes = (int)(endDate.getTime()-startDate.getTime());

            MessagePostProcessor messagePostProcessor = new MessagePostProcessor() {
                @Override
                public Message postProcessMessage(Message message) throws AmqpException {
                    // ?????????????????????
                    message.getMessageProperties()
                            .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    // ????????????????????????????????????ms??????
                    message.getMessageProperties()
                            .setDelay(delayTimes);
                    return message;
                }
            };

            rabbitTemplate.convertAndSend(
                    RabbitMQDelayConfig.EXCHANGE_DELAY,
                    "publish.delay.display",
                    articleId,
                    messagePostProcessor);

            System.out.println("????????????-?????????????????????" + new Date());
        }


        /**
         * FIXME: ?????????????????????????????????????????????????????????????????????
         */
        // ??????????????????AI??????????????????????????????????????????????????????
        String reviewTextResult = aliTextReviewUtils.reviewTextContent(newArticleBO.getContent());

        if (reviewTextResult
                .equalsIgnoreCase(ArticleReviewLevel.PASS.type)) {
            // ???????????????????????????????????????????????????
            this.updateArticleStatus(articleId, ArticleReviewStatus.SUCCESS.type);
        } else if (reviewTextResult
                .equalsIgnoreCase(ArticleReviewLevel.REVIEW.type)) {
            // ?????????????????????????????????????????????????????????
            this.updateArticleStatus(articleId, ArticleReviewStatus.WAITING_MANUAL.type);
        } else if (reviewTextResult
                .equalsIgnoreCase(ArticleReviewLevel.BLOCK.type)) {
            // ??????????????????????????????????????????????????????
            this.updateArticleStatus(articleId, ArticleReviewStatus.FAILED.type);
        }

    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateArticleStatus(String articleId, Integer pendingStatus) {
        Example example = new Example(Article.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("id", articleId);

        Article pendingArticle = new Article();
        pendingArticle.setArticleStatus(pendingStatus);

        int res = articleMapper.updateByExampleSelective(pendingArticle, example);
        if (res != 1) {
            GraceException.display(ResponseStatusEnum.ARTICLE_REVIEW_ERROR);
        }

        // ??????????????????????????????article???????????????????????????????????????es???
        if (pendingStatus.equals(ArticleReviewStatus.SUCCESS.type)) {
            Article result = articleMapper.selectByPrimaryKey(articleId);
            // ?????????????????????????????????????????????????????????????????????es???
            if (result.getIsAppoint().equals(ArticleAppointType.IMMEDIATELY.type)) {
                // ??????es
                ArticleEO articleEO = new ArticleEO();
                BeanUtils.copyProperties(result, articleEO);
                IndexQuery iq = new IndexQueryBuilder().withObject(articleEO).build();
                esTemplate.index(iq);

                // ??????
                LocalDateTime now = LocalDateTime.now();
                Duration duration = Duration.between(epoch,now);
                long millis = duration.toMillis();
                redis.incrScore(REDIS_ARTICLE_TOP,articleId,millis/(1000*3600*24));

                // ??????????????????????????????????????????html
                String articleMongoId = null;
                try {
                    // ??????GridFS???
                    articleMongoId = createArticleHTMLToGridFS(articleId);
                    // ?????????????????????????????????????????????
                    updateArticleToGridFS(articleId, articleMongoId);
                    // ???????????????mq?????????????????????????????????????????????html
                    doDownloadArticleHTMLByMQ(articleId, articleMongoId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // FIXME: ??????????????????????????????????????????????????????es????????????????????????????????????????????????
        }
    }

    private void doDownloadArticleHTMLByMQ(String articleId, String articleMongoId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_ARTICLE,
                "article.download.do",
                articleId + "," + articleMongoId);
    }

    // ????????????HTML
    public String createArticleHTMLToGridFS(String articleId) throws Exception {

        Configuration cfg = new Configuration(Configuration.getVersion());
        String classpath = this.getClass().getResource("/").getPath();
        cfg.setDirectoryForTemplateLoading(new File(classpath + "templates"));

        Template template = cfg.getTemplate("detail.ftl", "utf-8");

        // ???????????????????????????
        ArticleDetailVO detailVO = getArticleDetail(articleId);
        Map<String, Object> map = new HashMap<>();
        map.put("articleDetail", detailVO);

        String htmlContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, map);

        InputStream inputStream = IOUtils.toInputStream(htmlContent);
        ObjectId fileId = gridFSBucket.uploadFromStream(detailVO.getId() + ".html",inputStream);
        return fileId.toString();
    }

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    ArticlePortalController articlePortalController;
     //??????????????????rest???????????????????????????
    public ArticleDetailVO getArticleDetail(String articleId) {
        GraceJSONResult bodyResult = articlePortalController.detail(articleId);
        ArticleDetailVO detailVO = null;
        if (bodyResult.getStatus() == 200) {
            String detailJson = JsonUtils.objectToJson(bodyResult.getData());
            detailVO = JsonUtils.jsonToPojo(detailJson, ArticleDetailVO.class);
        }
        return detailVO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateArticleToGridFS(String articleId, String articleMongoId) {
        Article pendingArticle = new Article();
        pendingArticle.setId(articleId);
        pendingArticle.setMongoFileId(articleMongoId);
        articleMapper.updateByPrimaryKeySelective(pendingArticle);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateArticleToPublish(String articleId) {
        Article article = new Article();
        article.setId(articleId);
        article.setIsAppoint(ArticleAppointType.IMMEDIATELY.type);
        articleMapper.updateByPrimaryKeySelective(article);

        Article result = articleMapper.selectByPrimaryKey(articleId);
        // ??????es
        ArticleEO articleEO = new ArticleEO();
        BeanUtils.copyProperties(result, articleEO);
        IndexQuery iq = new IndexQueryBuilder().withObject(articleEO).build();
        esTemplate.index(iq);

        // ??????
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(epoch,now);
        long millis = duration.toMillis();
        redis.incrScore(REDIS_ARTICLE_TOP,articleId,millis/(1000*3600*24));

        // ??????????????????????????????????????????html
        String articleMongoId = null;
        try {
            articleMongoId = createArticleHTMLToGridFS(articleId);
            // ?????????????????????????????????????????????
            updateArticleToGridFS(articleId, articleMongoId);
            // ???????????????mq?????????????????????????????????????????????html
            doDownloadArticleHTMLByMQ(articleId, articleMongoId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public PagedGridResult queryMyArticleList(String userId,
                                              String keyword,
                                              Integer status,
                                              Date startDate,
                                              Date endDate,
                                              Integer page,
                                              Integer pageSize) {

        Example example = new Example(Article.class);
        example.orderBy("createTime").desc();
        Example.Criteria criteria = example.createCriteria();

        criteria.andEqualTo("publishUserId", userId);

        if (StringUtils.isNotBlank(keyword)) {
            criteria.andLike("title", "%" + keyword + "%");
        }

        if (ArticleReviewStatus.isArticleStatusValid(status)) {
            criteria.andEqualTo("articleStatus", status);
        }

        if (status != null && status == 12) {
            criteria.andEqualTo("articleStatus", ArticleReviewStatus.REVIEWING.type)
                    .orEqualTo("articleStatus", ArticleReviewStatus.WAITING_MANUAL.type);
        }

        criteria.andEqualTo("isDelete", YesOrNo.NO.type);

        if (startDate != null) {
            criteria.andGreaterThanOrEqualTo("publishTime", startDate);
        }
        if (endDate != null) {
            criteria.andLessThanOrEqualTo("publishTime", endDate);
        }

        PageHelper.startPage(page, pageSize);
        List<Article> list = articleMapper.selectByExample(example);
        return setterPagedGrid(list, page);
    }

    @Override
    public PagedGridResult queryAllArticleListAdmin(Integer status, Integer page, Integer pageSize) {
        Example articleExample = new Example(Article.class);
        articleExample.orderBy("createTime").desc();

        Example.Criteria criteria = articleExample.createCriteria();
        if (ArticleReviewStatus.isArticleStatusValid(status)) {
            criteria.andEqualTo("articleStatus", status);
        }

        // ????????????????????????????????????????????????????????????????????????
        if (status != null && status == 12) {
            criteria.andEqualTo("articleStatus", ArticleReviewStatus.REVIEWING.type)
                    .orEqualTo("articleStatus", ArticleReviewStatus.WAITING_MANUAL.type);
        }

        //isDelete ?????????0
        criteria.andEqualTo("isDelete", YesOrNo.NO.type);

        /**
         * page: ?????????
         * pageSize: ??????????????????
         */
        PageHelper.startPage(page, pageSize);
        List<Article> list = articleMapper.selectByExample(articleExample);
        return setterPagedGrid(list, page);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteArticle(String userId, String articleId) {
        Example articleExample = makeExampleCriteria(userId, articleId);

        Article pending = new Article();
        pending.setIsDelete(YesOrNo.YES.type);

        int result = articleMapper.updateByExampleSelective(pending, articleExample);
        if (result != 1) {
            GraceException.display(ResponseStatusEnum.ARTICLE_DELETE_ERROR);
        }

        deleteHTML(articleId);

        esTemplate.delete(ArticleEO.class, articleId);
    }

    /**
     * ??????????????????????????????????????????html
     */
    private void deleteHTML(String articleId) {
        // 1. ???????????????mongoFileId
        Article pending = articleMapper.selectByPrimaryKey(articleId);

        if (StringUtils.isBlank(pending.getMongoFileId())) {
            return;
        }

        String articleMongoId = pending.getMongoFileId();

        // 2. ??????GridFS????????????
        gridFSBucket.delete(new ObjectId(articleMongoId));

        // 3. ??????????????????HTML??????
        doDeleteArticleHTMLByMQ(articleId);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;
    private void doDeleteArticleHTMLByMQ(String articleId) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_ARTICLE,
                "article.html.download.do", articleId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void withdrawArticle(String userId, String articleId) {
        Example articleExample = makeExampleCriteria(userId, articleId);

        Article pending = new Article();
        pending.setArticleStatus(ArticleReviewStatus.WITHDRAW.type);

        int result = articleMapper.updateByExampleSelective(pending, articleExample);
        if (result != 1) {
            GraceException.display(ResponseStatusEnum.ARTICLE_WITHDRAW_ERROR);
        }

        deleteHTML(articleId);

        esTemplate.delete(ArticleEO.class, articleId);
    }

    private Example makeExampleCriteria(String userId, String articleId) {
        Example articleExample = new Example(Article.class);
        Example.Criteria criteria = articleExample.createCriteria();
        criteria.andEqualTo("publishUserId", userId);
        criteria.andEqualTo("id", articleId);
        return articleExample;
    }
}
