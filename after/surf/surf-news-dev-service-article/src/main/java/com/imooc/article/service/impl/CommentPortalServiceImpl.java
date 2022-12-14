package com.imooc.article.service.impl;

import com.github.pagehelper.PageHelper;
import com.imooc.api.controller.article.CommentControllerApi;
import com.imooc.api.service.BaseService;
import com.imooc.article.mapper.ArticleMapper;
import com.imooc.article.mapper.CommentsMapper;
import com.imooc.article.mapper.CommentsMapperCustom;
import com.imooc.article.service.ArticlePortalService;
import com.imooc.article.service.CommentPortalService;
import com.imooc.enums.ArticleReviewStatus;
import com.imooc.enums.YesOrNo;
import com.imooc.pojo.Article;
import com.imooc.pojo.Comments;
import com.imooc.pojo.vo.ArticleDetailVO;
import com.imooc.pojo.vo.CommentsVO;
import com.imooc.utils.PagedGridResult;
import com.imooc.utils.SensitiveFilter;
import org.apache.commons.lang3.StringUtils;
import org.n3r.idworker.Sid;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CommentPortalServiceImpl extends BaseService implements CommentPortalService {

    @Autowired
    private Sid sid;

    @Autowired
    private ArticlePortalService articlePortalService;

    @Autowired
    private CommentsMapper commentsMapper;

    @Autowired
    private CommentsMapperCustom commentsMapperCustom;

    @Autowired
    private SensitiveFilter sensitiveFilter;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void createComment(String articleId,
                                                 String fatherCommentId,
                                                 String content,
                                                 String userId,
                                                 String nickname,
                                                 String face) {

        if(!"<p><br></p>".equals(content)){
            String commentId = sid.nextShort();
            String newContent = sensitiveFilter.filter(content);

            ArticleDetailVO article
                    = articlePortalService.queryDetail(articleId);

            Comments comments = new Comments();
            comments.setId(commentId);

            comments.setWriterId(article.getPublishUserId());
            comments.setArticleTitle(article.getTitle());
            comments.setArticleCover(Objects.equals(article.getCover(), "") ?null:article.getCover());
            comments.setArticleId(articleId);

            comments.setFatherId(fatherCommentId);
            comments.setCommentUserId(userId);
            comments.setCommentUserNickname(nickname);
            comments.setCommentUserFace(face);

            comments.setContent(newContent);
            comments.setCreateTime(LocalDateTime.now());

            commentsMapper.insert(comments);

            // ???????????????
            redis.increment(REDIS_ARTICLE_COMMENT_COUNTS + ":" + articleId, 1);

            // ????????????
            redis.incrScore(REDIS_ARTICLE_TOP,articleId,2);
        }
    }

    @Override
    public PagedGridResult queryArticleComments(String articleId,
                                                Integer page,
                                                Integer pageSize) {
        Map<String, Object> map = new HashMap<>();
        map.put("articleId", articleId);

        PageHelper.startPage(page, pageSize);
        List<CommentsVO> list = commentsMapperCustom.queryArticleCommentList(map);
        return setterPagedGrid(list, page);
    }

    @Override
    public PagedGridResult queryWriterCommentsMng(String writerId, Integer page, Integer pageSize) {

        Comments comment = new Comments();
        comment.setWriterId(writerId);

        PageHelper.startPage(page, pageSize);
        List<Comments> list = commentsMapper.select(comment);
        return setterPagedGrid(list, page);
    }

    @Override
    public void deleteComment(String writerId, String commentId) {
        Comments comment = new Comments();
        comment.setId(commentId);
        comment.setWriterId(writerId);

        commentsMapper.delete(comment);
    }
}
