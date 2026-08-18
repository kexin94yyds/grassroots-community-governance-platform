package com.cunzhi.governance.event.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface EventCategoryMapper {

    @Select("""
            select id, category_code as code, category_name as name, description,
                   sort_no as sortNo, status, version
            from event_category
            order by sort_no, id
            """)
    List<CategoryRow> findAll();

    @Select("""
            select id, category_code as code, category_name as name, description,
                   sort_no as sortNo, status, version
            from event_category where id = #{id}
            """)
    CategoryRow findById(@Param("id") long id);

    @Select("""
            select id, category_code as code, category_name as name, description,
                   sort_no as sortNo, status, version
            from event_category where id = #{id}
            for update
            """)
    CategoryRow findByIdForUpdate(@Param("id") long id);

    @Select("select count(*) from event_category where category_code = #{code}")
    int countByCode(@Param("code") String code);

    @Insert("""
            insert into event_category (category_code, category_name, description, sort_no, status)
            values (#{code}, #{name}, #{description}, #{sortNo}, #{status})
            """)
    int insert(CategoryRow row);

    @Select("select id from event_category where category_code = #{code}")
    Long findIdByCode(@Param("code") String code);

    @Update("""
            update event_category
            set category_name = #{name}, description = #{description}, sort_no = #{sortNo}, status = #{status},
                version = version + 1
            where id = #{id} and version = #{version}
              and (
                #{status} <> 'DISABLED'
                or status = 'DISABLED'
                or not exists (
                  select 1 from governance_event e
                  where e.category_id = #{id}
                    and e.status not in ('CLOSED', 'REJECTED', 'CANCELLED')
                )
              )
            """)
    int update(CategoryRow row);

    @Select("""
            select count(*) from governance_event
            where category_id = #{categoryId}
              and status not in ('CLOSED', 'REJECTED', 'CANCELLED')
            """)
    int countNonTerminalEvents(@Param("categoryId") long categoryId);

    record CategoryRow(
            Long id, String code, String name, String description, int sortNo, String status, int version
    ) {
    }
}
