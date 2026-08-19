package com.cunzhi.governance.serviceapplication.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ServiceCatalogMapper {

    @Select("""
            <script>
            select id, service_code as code, service_name as name, description,
                   sort_no as sortNo, status, version
            from service_catalog
            <if test="!includeDisabled">where status = 'ENABLED'</if>
            order by sort_no, id
            </script>
            """)
    List<CatalogRow> findAll(@Param("includeDisabled") boolean includeDisabled);

    @Select("""
            select id, service_code as code, service_name as name, description,
                   sort_no as sortNo, status, version
            from service_catalog where id = #{id}
            """)
    CatalogRow findById(@Param("id") long id);

    @Select("""
            select id, service_code as code, service_name as name, description,
                   sort_no as sortNo, status, version
            from service_catalog where id = #{id} for update
            """)
    CatalogRow findByIdForUpdate(@Param("id") long id);

    @Select("""
            select id, service_code as code, service_name as name, description,
                   sort_no as sortNo, status, version
            from service_catalog where id = #{id} and status = 'ENABLED' for update
            """)
    CatalogRow findEnabledByIdForUpdate(@Param("id") long id);

    @Select("select count(*) from service_application where service_catalog_id = #{id} and status not in ('COMPLETED', 'REJECTED', 'CANCELLED')")
    int countOpenApplications(@Param("id") long id);

    @Insert("""
            insert into service_catalog (service_code, service_name, description, sort_no, status)
            values (#{code}, #{name}, #{description}, #{sortNo}, #{status})
            """)
    int insert(
            @Param("code") String code,
            @Param("name") String name,
            @Param("description") String description,
            @Param("sortNo") int sortNo,
            @Param("status") String status
    );

    @Select("select id from service_catalog where service_code = #{code}")
    Long findIdByCode(@Param("code") String code);

    @Update("""
            update service_catalog
            set service_name = #{name}, description = #{description}, sort_no = #{sortNo},
                status = #{status}, version = version + 1
            where id = #{id} and version = #{version}
            """)
    int update(
            @Param("id") long id,
            @Param("name") String name,
            @Param("description") String description,
            @Param("sortNo") int sortNo,
            @Param("status") String status,
            @Param("version") int version
    );

    record CatalogRow(Long id, String code, String name, String description, int sortNo, String status, int version) {
    }
}
