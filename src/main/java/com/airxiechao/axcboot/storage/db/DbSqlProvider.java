package com.airxiechao.axcboot.storage.db;

import com.airxiechao.axcboot.storage.annotation.Table;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.jdbc.SQL;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbSqlProvider {

    public <T> String selectById(@Param("id") long id, Class<T> tClass){
        Table table = tClass.getAnnotation(Table.class);
        String tableName = table.value();

        String sql = new SQL()
                .SELECT("*")
                .FROM(tableName)
                .WHERE("id = #{id}")
                .toString();

        return sql;
    }

    public String parameterizedSql(@Param("sql") String sql, @Param("params") Map params){
        String sql2 = "<script><![CDATA[" + sql.replaceAll("#\\{", "#\\{params\\.") + "]]></script>";
        return sql2;
    }

    public <T> String deleteById(@Param("id") long id, Class<T> tClass){
        Table table = tClass.getAnnotation(Table.class);
        String tableName = table.value();

        String sql = new SQL()
                .DELETE_FROM(tableName)
                .WHERE("id = #{id}")
                .toString();

        return sql;
    }

    public String insert(Object object){
        Table table = object.getClass().getAnnotation(Table.class);
        String tableName = table.value();

        SQL sql = new SQL();
        sql.INSERT_INTO(tableName);
        for(Field field : object.getClass().getDeclaredFields()){
            if(field.getName().equals("id") || Modifier.isStatic(field.getModifiers())){
                continue;
            }

            sql.VALUES("`"+camelCaseToUnderscore(field.getName())+"`","#{"+field.getName()+"}");
        }

        return sql.toString();
    }

    public String insertWithId(Object object){
        Table table = object.getClass().getAnnotation(Table.class);
        String tableName = table.value();

        SQL sql = new SQL();
        sql.INSERT_INTO(tableName);
        for(Field field : object.getClass().getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers())){
                continue;
            }

            sql.VALUES("`"+camelCaseToUnderscore(field.getName())+"`","#{"+field.getName()+"}");
        }

        return sql.toString();
    }

    public String insertBatch(@Param("list") List<Object> list){
        Object object = list.get(0);

        Table table = object.getClass().getAnnotation(Table.class);
        String tableName = table.value();

        Map<String, String> cols = new HashMap<>();
        for(Field field : object.getClass().getDeclaredFields()){
            if(field.getName().equals("id") || Modifier.isStatic(field.getModifiers())){
                continue;
            }

            cols.put("`"+camelCaseToUnderscore(field.getName())+"`", "#{element."+field.getName()+"}");
        }
        String colStr = String.join(",", cols.keySet());
        String valStr = String.join(",", cols.values());

        String sql = "<script>" +
                "INSERT INTO " + tableName + " " +
                "("+colStr+")" +
                "VALUES " +
                "<foreach collection='list' item='element' open='(' separator='),(' close=')'> " +
                valStr +
                "</foreach>" +
                "</script>";

        return sql;
    }

    public String insertOrUpdate(Object object){
        Table table = object.getClass().getAnnotation(Table.class);
        String tableName = table.value();

        SQL sql = new SQL();
        sql.INSERT_INTO(tableName);
        List<String> updates = new ArrayList<>();
        for(Field field : object.getClass().getDeclaredFields()){
            if(field.getName().equals("id") || Modifier.isStatic(field.getModifiers())){
                continue;
            }

            String col = "`"+camelCaseToUnderscore(field.getName())+"`";
            updates.add(col+"=VALUES("+col+")");
            sql.VALUES("`"+camelCaseToUnderscore(field.getName())+"`","#{"+field.getName()+"}");
        }

        String updateStr = String.join(",", updates);

        return sql.toString() + " ON DUPLICATE KEY UPDATE " + updateStr;
    }

    public String insertOrUpdateBatch(@Param("list") List<Object> list){
        Object object = list.get(0);

        Table table = object.getClass().getAnnotation(Table.class);
        String tableName = table.value();

        Map<String, String> cols = new HashMap<>();
        List<String> updates = new ArrayList<>();
        for(Field field : object.getClass().getDeclaredFields()){
            if(field.getName().equals("id") || Modifier.isStatic(field.getModifiers())){
                continue;
            }

            String col = "`"+camelCaseToUnderscore(field.getName())+"`";
            updates.add(col+"=VALUES("+col+")");
            cols.put(col, "#{element."+field.getName()+"}");
        }
        String colStr = String.join(",", cols.keySet());
        String valStr = String.join(",", cols.values());
        String updateStr = String.join(",", updates);

        String sql = "<script>" +
                "INSERT INTO " + tableName + " " +
                "("+colStr+") " +
                "VALUES " +
                "<foreach collection='list' item='element' open='(' separator='),(' close=')'> " +
                valStr +
                "</foreach> " +
                "ON DUPLICATE KEY UPDATE " +
                updateStr +
                "</script>";

        return sql;
    }

    public String update(Object object){
        Table table = object.getClass().getAnnotation(Table.class);
        String tableName = table.value();

        SQL sql = new SQL();
        sql.UPDATE(tableName);
        for(Field field : object.getClass().getDeclaredFields()){
            if(field.getName().equals("id") || Modifier.isStatic(field.getModifiers())){
                continue;
            }

            sql.SET("`"+camelCaseToUnderscore(field.getName()) + "` = #{"+field.getName()+"}");
        }
        sql.WHERE("id = #{id}");

        return sql.toString();
    }

    private String camelCaseToUnderscore(String str) {
        return str.replaceAll("[A-Z]", "_$0").toLowerCase();
    }
}
