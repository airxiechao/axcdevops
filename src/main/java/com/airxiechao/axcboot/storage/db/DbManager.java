package com.airxiechao.axcboot.storage.db;

import com.airxiechao.axcboot.storage.annotation.Table;
import com.airxiechao.axcboot.util.StringUtil;
import com.alibaba.fastjson.JSON;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbManager {

    private static final Logger logger = LoggerFactory.getLogger(DbManager.class);

    private static DbManager ourInstance = new DbManager();

    public static DbManager getInstance() {
        return ourInstance;
    }


    private static final String DEFAULT_DATASOURCE = "default";

    private Map<String, SqlSessionFactory> sqlSessionFactoryMap = new HashMap<>();

    private DbManager() {
        List<String> envIds;
        try (InputStream inputStream = Resources.getResourceAsStream("mybatis.xml")){
            envIds = parseEnviromentIds(inputStream);
        }catch (Exception e){
            logger.error("db manager parse enviroment ids error.", e);
            return;
        }

        // default datasource
        try (InputStream inputStream = Resources.getResourceAsStream("mybatis.xml")){
            SqlSessionFactory defaultSqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
            defaultSqlSessionFactory.getConfiguration().addMapper(DbMapper.class);
            sqlSessionFactoryMap.put(DEFAULT_DATASOURCE, defaultSqlSessionFactory);
        }catch (Exception e){
            logger.error("db manager init default datasource error.", e);
            return;
        }

        // named statsources
        for(String envId : envIds){
            try (InputStream inputStream = Resources.getResourceAsStream("mybatis.xml")){
                SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream, envId);
                sqlSessionFactory.getConfiguration().addMapper(DbMapper.class);
                sqlSessionFactoryMap.put(envId, sqlSessionFactory);
            }catch (Exception e){
                logger.error("db manager init datasource [{}] error.", envId, e);
            }
        }
    }

    private List<String> parseEnviromentIds(InputStream inputStream){
        List<String> envIds = new ArrayList<>();

        try{
            DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
            Document xml = xmlBuilder.parse(inputStream);
            NodeList envNodes = xml.getDocumentElement().getElementsByTagName("environment");
            for(int i = 0; i < envNodes.getLength(); ++i){
                Node envNode = envNodes.item(i);
                if(envNode.getNodeType() == Node.ELEMENT_NODE){
                    Element envElement = (Element)envNode;
                    String envId = envElement.getAttribute("id");
                    envIds.add(envId);
                }
            }
        }catch (Exception e){
            logger.error("parse db enviroment ids error.", e);
        }

        return envIds;
    }

    // ---------------------------------------- seesion ------------------------------------------

    public SqlSession openSession(String datasource){
        SqlSessionFactory sqlSessionFactory = getSqlSessionFactory(datasource);
        return sqlSessionFactory.openSession();
    }

    public SqlSession openSession(String datasource, boolean autoCommit){
        SqlSessionFactory sqlSessionFactory = getSqlSessionFactory(datasource);
        return sqlSessionFactory.openSession(autoCommit);
    }

    private SqlSessionFactory getSqlSessionFactory(String datasource){
        SqlSessionFactory sqlSessionFactory = null;
        if(!StringUtil.isBlank(datasource)){
            sqlSessionFactory = sqlSessionFactoryMap.get(datasource);
        }
        if(null == sqlSessionFactory){
            sqlSessionFactory = sqlSessionFactoryMap.get(DEFAULT_DATASOURCE);
        }

        return sqlSessionFactory;
    }

    // ---------------------------------------- query -----------------------------------------------

    private <T> String getDatasource(Class<T> tClass){
        Table table = tClass.getAnnotation(Table.class);
        String datasource = null;
        if(null != table){
            datasource = table.datasource();
            if(!StringUtil.isBlank(datasource)){
                return datasource;
            }

            String datasourceMethod = table.datasourceMethod();
            if(!StringUtil.isBlank(datasourceMethod)){
                try {
                    Method method = tClass.getMethod(datasourceMethod);
                    datasource = (String)method.invoke(null);
                    return datasource;
                } catch (Exception e) {
                    logger.error("call datasourceMethod {}.{} error", tClass.getName(), datasourceMethod);
                }
            }
        }

        return datasource;
    }

    public <T> T getById(long id, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            Map map = mapper.selectById(id, tClass);
            T ret = JSON.parseObject(JSON.toJSONString(map), tClass);
            return ret;
        }
    }

    public <T> int deleteById(long id, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource, true)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            int ret = mapper.deleteById(id, tClass);
            return ret;
        }
    }

    public int insert(Object object){
        String datasource = getDatasource(object.getClass());
        try(SqlSession session = openSession(datasource, true)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            int ret = mapper.insert(object);
            return ret;
        }
    }

    public int insertBatch(List<?> list){

        Class tClass = Object.class;
        if(list.size() > 0){
            tClass = list.get(0).getClass();
        }
        String datasource = getDatasource(tClass);

        try(SqlSession session = openSession(datasource, true)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            int ret = mapper.insertBatch(list);
            return ret;
        }
    }

    public int insertWithId(Object object){
        String datasource = getDatasource(object.getClass());
        try(SqlSession session = openSession(datasource, true)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            int ret = mapper.insertWithId(object);
            return ret;
        }
    }

    public int insertOrUpdate(Object object){
        String datasource = getDatasource(object.getClass());
        try(SqlSession session = openSession(datasource, true)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            int ret = mapper.insertOrUpdate(object);
            return ret;
        }
    }

    public int insertOrUpdateBatch(List<?> list){

        Class tClass = Object.class;
        if(list.size() > 0){
            tClass = list.get(0).getClass();
        }
        String datasource = getDatasource(tClass);

        try(SqlSession session = openSession(datasource, true)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            int ret = mapper.insertOrUpdateBatch(list);
            return ret;
        }
    }

    public int insertOrUpdateBatch(List<?> list, int batchSize) {

        Class tClass = Object.class;
        if(list.size() > 0){
            tClass = list.get(0).getClass();
        }
        String datasource = getDatasource(tClass);

        try (SqlSession session = openSession(datasource, true)) {
            DbMapper mapper = session.getMapper(DbMapper.class);

            int ret = 0;
            List<Object> sub = new ArrayList<>();
            for(Object object : list){
                sub.add(object);

                if(sub.size() == batchSize){
                    ret += mapper.insertOrUpdateBatch(sub);
                    sub.clear();
                }
            }

            if(sub.size() > 0){
                ret += mapper.insertOrUpdateBatch(sub);
                sub.clear();
            }

            return ret;
        }
    }

    public int update(Object object){
        String datasource = getDatasource(object.getClass());
        try(SqlSession session = openSession(datasource, true)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            int ret = mapper.update(object);
            return ret;
        }
    }

    public <T> List<T> selectBySql(String sql, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            List<Map> list = mapper.selectBySql(sql, null);

            List<T> ret = new ArrayList<>();
            for(Map map : list){
                T t = JSON.parseObject(JSON.toJSONString(map), tClass);
                ret.add(t);
            }

            return ret;
        }
    }

    public <T> List<T> selectBySql(String sql, Map params, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            List<Map> list = mapper.selectBySql(sql, params);

            List<T> ret = new ArrayList<>();
            for(Map map : list){
                T t = JSON.parseObject(JSON.toJSONString(map), tClass);
                ret.add(t);
            }

            return ret;
        }
    }

    public <T> T selectFirstBySql(String sql, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            List<Map> list = mapper.selectBySql(sql, null);

            List<T> ret = new ArrayList<>();
            for(Map map : list){
                T t = JSON.parseObject(JSON.toJSONString(map), tClass);
                ret.add(t);
                break;
            }

            if(ret.size() > 0){
                return ret.get(0);
            }else{
                return null;
            }
        }
    }

    public <T> T selectFirstBySql(String sql, Map params, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            List<Map> list = mapper.selectBySql(sql, params);

            List<T> ret = new ArrayList<>();
            for(Map map : list){
                T t = JSON.parseObject(JSON.toJSONString(map), tClass);
                ret.add(t);
                break;
            }

            if(ret.size() > 0){
                return ret.get(0);
            }else{
                return null;
            }
        }
    }

    public <T> Integer intBySql(String sql, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            return mapper.intBySql(sql, null);
        }
    }

    public <T> Integer intBySql(String sql, Map params, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            return mapper.intBySql(sql, params);
        }
    }

    public <T> Double doubleBySql(String sql, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            return mapper.doubleBySql(sql, null);
        }
    }

    public <T> Double doubleBySql(String sql, Map params, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            return mapper.doubleBySql(sql, params);
        }
    }

    public <T> int updateBySql(String sql, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource, true)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            int ret = mapper.updateBySql(sql, null);
            return ret;
        }
    }

    public <T> int updateBySql(String sql, Map params, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource,true)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            int ret = mapper.updateBySql(sql, params);
            return ret;
        }
    }

    public <T> int deleteBySql(String sql, Class<T> tClass){
        String datasource = getDatasource(tClass);
        return deleteBySql(sql, datasource);
    }

    public <T> int deleteBySql(String sql, String datasource){
        try(SqlSession session = openSession(datasource,true)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            int ret = mapper.deleteBySql(sql, null);
            return ret;
        }
    }

    public <T> int deleteBySql(String sql, Map params, Class<T> tClass){
        String datasource = getDatasource(tClass);
        try(SqlSession session = openSession(datasource, true)){
            DbMapper mapper = session.getMapper(DbMapper.class);
            int ret = mapper.deleteBySql(sql, params);
            return ret;
        }
    }

    public boolean executeTransaction(DbTransaction transaction, String datasrouce){
        if(StringUtil.isBlank(datasrouce)){
            datasrouce = DEFAULT_DATASOURCE;
        }

        SqlSession session = DbManager.getInstance().openSession(datasrouce);
        DbMapper mapper = session.getMapper(DbMapper.class);

        boolean success = false;
        try{
            transaction.execute(mapper);

            session.commit();
            success = true;
        }catch (Exception e){
            session.rollback();
            logger.error("transaction rollback", e);
        }finally {
            session.close();
        }

        return success;
    }

    public <T> boolean executeTransaction(DbTransaction transaction, Class<T> tClass){
        String datasource = getDatasource(tClass);
        return executeTransaction(transaction, datasource);
    }
}

