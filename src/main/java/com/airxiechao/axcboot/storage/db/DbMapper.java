package com.airxiechao.axcboot.storage.db;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface DbMapper {

    @SelectProvider(type = DbSqlProvider.class, method = "selectById")
    Map selectById(@Param("id") long id, Class<?> tClass);

    @DeleteProvider(type = DbSqlProvider.class, method = "deleteById")
    int deleteById(@Param("id") long id, Class<?> tClass);

    @InsertProvider(type = DbSqlProvider.class, method = "insert")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Object object);

    @InsertProvider(type = DbSqlProvider.class, method = "insertWithId")
    int insertWithId(Object object);

    @InsertProvider(type = DbSqlProvider.class, method = "insertBatch")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertBatch(@Param("list") List<?> list);

    @UpdateProvider(type = DbSqlProvider.class, method = "insertOrUpdate")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertOrUpdate(Object object);

    @UpdateProvider(type = DbSqlProvider.class, method = "insertOrUpdateBatch")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertOrUpdateBatch(@Param("list") List<?> list);

    @UpdateProvider(type = DbSqlProvider.class, method = "update")
    int update(Object object);

    @SelectProvider(type = DbSqlProvider.class, method = "parameterizedSql")
    List<Map> selectBySql(@Param("sql") String sql, @Param("params") Map params);

    @SelectProvider(type = DbSqlProvider.class, method = "parameterizedSql")
    Integer intBySql(@Param("sql") String sql, @Param("params") Map params);

    @SelectProvider(type = DbSqlProvider.class, method = "parameterizedSql")
    Double doubleBySql(@Param("sql") String sql, @Param("params") Map params);

    @UpdateProvider(type = DbSqlProvider.class, method = "parameterizedSql")
    int updateBySql(@Param("sql") String sql, @Param("params") Map params);

    @DeleteProvider(type = DbSqlProvider.class, method = "parameterizedSql")
    int deleteBySql(@Param("sql") String sql, @Param("params") Map params);
}
