package com.cunzhi.governance.resident.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface HouseholdMapper {

    @Select("""
            <script>
            select household.id, household.household_no as householdNo,
                   household.grid_id as gridId, grid.area_name as gridName,
                   household.building_no as buildingNo, household.unit_no as unitNo,
                   household.room_no as roomNo, household.address,
                   household.status, household.version
            from household household
            join grid_area grid on grid.id = household.grid_id
            where 1 = 1
              <if test="keyword != null and keyword != ''">
                and (household.household_no like concat('%', #{keyword}, '%')
                     or household.address like concat('%', #{keyword}, '%')
                     or household.building_no like concat('%', #{keyword}, '%')
                     or household.unit_no like concat('%', #{keyword}, '%')
                     or household.room_no like concat('%', #{keyword}, '%'))
              </if>
              <if test="requestedGridId != null">and household.grid_id = #{requestedGridId}</if>
              <if test="status != null and status != ''">and household.status = #{status}</if>
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and household.grid_id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                      #{gridId}
                    </foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            order by household.household_no
            limit #{size} offset #{offset}
            </script>
            """)
    List<HouseholdRow> findPage(
            @Param("keyword") String keyword,
            @Param("requestedGridId") Long requestedGridId,
            @Param("status") String status,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            <script>
            select count(*)
            from household household
            where 1 = 1
              <if test="keyword != null and keyword != ''">
                and (household.household_no like concat('%', #{keyword}, '%')
                     or household.address like concat('%', #{keyword}, '%')
                     or household.building_no like concat('%', #{keyword}, '%')
                     or household.unit_no like concat('%', #{keyword}, '%')
                     or household.room_no like concat('%', #{keyword}, '%'))
              </if>
              <if test="requestedGridId != null">and household.grid_id = #{requestedGridId}</if>
              <if test="status != null and status != ''">and household.status = #{status}</if>
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and household.grid_id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                      #{gridId}
                    </foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            </script>
            """)
    long count(
            @Param("keyword") String keyword,
            @Param("requestedGridId") Long requestedGridId,
            @Param("status") String status,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            select household.id, household.household_no as householdNo,
                   household.grid_id as gridId, grid.area_name as gridName,
                   household.building_no as buildingNo, household.unit_no as unitNo,
                   household.room_no as roomNo, household.address,
                   household.status, household.version
            from household household
            join grid_area grid on grid.id = household.grid_id
            where household.id = #{id}
            """)
    HouseholdRow findById(@Param("id") long id);

    @Insert("""
            insert into household
              (household_no, grid_id, building_no, unit_no, room_no, address, status)
            values
              (#{householdNo}, #{gridId}, #{buildingNo}, #{unitNo}, #{roomNo}, #{address}, 'ACTIVE')
            """)
    int insert(
            @Param("householdNo") String householdNo,
            @Param("gridId") long gridId,
            @Param("buildingNo") String buildingNo,
            @Param("unitNo") String unitNo,
            @Param("roomNo") String roomNo,
            @Param("address") String address
    );

    @Select("select id from household where household_no = #{householdNo}")
    Long findIdByHouseholdNo(@Param("householdNo") String householdNo);

    @Update("""
            update household
            set building_no = #{buildingNo}, unit_no = #{unitNo}, room_no = #{roomNo},
                address = #{address}, version = version + 1
            where id = #{id} and version = #{version}
            """)
    int update(
            @Param("id") long id,
            @Param("buildingNo") String buildingNo,
            @Param("unitNo") String unitNo,
            @Param("roomNo") String roomNo,
            @Param("address") String address,
            @Param("version") int version
    );

    @Update("""
            update household set status = #{status}, version = version + 1
            where id = #{id} and version = #{version}
            """)
    int updateStatus(
            @Param("id") long id,
            @Param("status") String status,
            @Param("version") int version
    );

    @Select("""
            select count(*) from resident
            where household_id = #{householdId} and status = 'ACTIVE'
            """)
    int countActiveResidents(@Param("householdId") long householdId);

    record HouseholdRow(
            Long id,
            String householdNo,
            Long gridId,
            String gridName,
            String buildingNo,
            String unitNo,
            String roomNo,
            String address,
            String status,
            int version
    ) {
    }
}
