package com.cunzhi.governance.resident.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ResidentMapper {

    @Select("""
            select resident.id, resident.resident_no as residentNo,
                   resident.grid_id as gridId, grid.area_name as gridName,
                   resident.household_id as householdId, household.household_no as householdNo,
                   resident.real_name as realName, resident.gender,
                   resident.birth_date as birthDate,
                   resident.id_card_last4 as idCardLast4,
                   resident.phone_last4 as phoneLast4,
                   resident.address, resident.is_householder as householder,
                   cast(resident.special_group_tags as char) as specialGroupTags,
                   resident.remark, resident.status, resident.version
            from resident resident
            join grid_area grid on grid.id = resident.grid_id
            left join household household on household.id = resident.household_id
            where resident.id = #{id}
            """)
    Optional<ResidentRow> findById(@Param("id") long id);

    @Select("""
            select resident.id, resident.resident_no as residentNo,
                   resident.grid_id as gridId, grid.area_name as gridName,
                   resident.household_id as householdId, household.household_no as householdNo,
                   resident.real_name as realName, resident.gender,
                   resident.birth_date as birthDate,
                   resident.id_card_last4 as idCardLast4,
                   resident.phone_last4 as phoneLast4,
                   resident.address, resident.is_householder as householder,
                   cast(resident.special_group_tags as char) as specialGroupTags,
                   resident.remark, resident.status, resident.version
            from resident resident
            join grid_area grid on grid.id = resident.grid_id
            left join household household on household.id = resident.household_id
            where resident.user_id = #{userId}
              and resident.status = 'ACTIVE'
            """)
    Optional<ResidentRow> findByUserId(@Param("userId") long userId);

    @Select("select user_id from resident where id = #{id}")
    Long findLinkedUserId(@Param("id") long id);

    @Select("""
            <script>
            select count(*)
            from resident resident
            where 1 = 1
              <if test="keyword != null and keyword != ''">
                and (resident.resident_no like concat('%', #{keyword}, '%')
                     or resident.real_name like concat('%', #{keyword}, '%')
                     or resident.address like concat('%', #{keyword}, '%'))
              </if>
              <if test="idCardHash != null and idCardHash != ''">
                and resident.id_card_hash = #{idCardHash}
              </if>
              <if test="phoneHash != null and phoneHash != ''">
                and resident.phone_hash = #{phoneHash}
              </if>
              <if test="requestedGridId != null">and resident.grid_id = #{requestedGridId}</if>
              <if test="status != null and status != ''">and resident.status = #{status}</if>
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and resident.grid_id in
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
            @Param("idCardHash") String idCardHash,
            @Param("phoneHash") String phoneHash,
            @Param("requestedGridId") Long requestedGridId,
            @Param("status") String status,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds
    );

    @Select("""
            <script>
            select resident.id, resident.resident_no as residentNo,
                   resident.grid_id as gridId, grid.area_name as gridName,
                   resident.household_id as householdId, household.household_no as householdNo,
                   resident.real_name as realName, resident.gender,
                   resident.birth_date as birthDate,
                   resident.id_card_last4 as idCardLast4,
                   resident.phone_last4 as phoneLast4,
                   resident.address, resident.is_householder as householder,
                   cast(resident.special_group_tags as char) as specialGroupTags,
                   resident.remark, resident.status, resident.version
            from resident resident
            join grid_area grid on grid.id = resident.grid_id
            left join household household on household.id = resident.household_id
            where 1 = 1
              <if test="keyword != null and keyword != ''">
                and (resident.resident_no like concat('%', #{keyword}, '%')
                     or resident.real_name like concat('%', #{keyword}, '%')
                     or resident.address like concat('%', #{keyword}, '%'))
              </if>
              <if test="idCardHash != null and idCardHash != ''">
                and resident.id_card_hash = #{idCardHash}
              </if>
              <if test="phoneHash != null and phoneHash != ''">
                and resident.phone_hash = #{phoneHash}
              </if>
              <if test="requestedGridId != null">and resident.grid_id = #{requestedGridId}</if>
              <if test="status != null and status != ''">and resident.status = #{status}</if>
              <if test="!allAccess">
                <choose>
                  <when test="gridIds != null and gridIds.size() > 0">
                    and resident.grid_id in
                    <foreach collection="gridIds" item="gridId" open="(" separator="," close=")">
                      #{gridId}
                    </foreach>
                  </when>
                  <otherwise>and 1 = 0</otherwise>
                </choose>
              </if>
            order by resident.id desc
            limit #{size} offset #{offset}
            </script>
            """)
    List<ResidentRow> findPage(
            @Param("keyword") String keyword,
            @Param("idCardHash") String idCardHash,
            @Param("phoneHash") String phoneHash,
            @Param("requestedGridId") Long requestedGridId,
            @Param("status") String status,
            @Param("allAccess") boolean allAccess,
            @Param("gridIds") List<Long> gridIds,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Insert("""
            insert into resident
              (resident_no, grid_id, household_id, real_name, gender, birth_date,
               id_card_ciphertext, id_card_hash, id_card_last4,
               phone_ciphertext, phone_hash, phone_last4,
               address, is_householder, special_group_tags, status, remark, created_by)
            values
              (#{residentNo}, #{gridId}, #{householdId}, #{realName}, #{gender}, #{birthDate},
               #{idCardCiphertext}, #{idCardHash}, #{idCardLast4},
               #{phoneCiphertext}, #{phoneHash}, #{phoneLast4},
               #{address}, #{householder}, cast(#{specialGroupTags} as json),
               'ACTIVE', #{remark}, #{createdBy})
            """)
    int insert(
            @Param("residentNo") String residentNo,
            @Param("gridId") long gridId,
            @Param("householdId") Long householdId,
            @Param("realName") String realName,
            @Param("gender") String gender,
            @Param("birthDate") LocalDate birthDate,
            @Param("idCardCiphertext") byte[] idCardCiphertext,
            @Param("idCardHash") String idCardHash,
            @Param("idCardLast4") String idCardLast4,
            @Param("phoneCiphertext") byte[] phoneCiphertext,
            @Param("phoneHash") String phoneHash,
            @Param("phoneLast4") String phoneLast4,
            @Param("address") String address,
            @Param("householder") boolean householder,
            @Param("specialGroupTags") String specialGroupTags,
            @Param("remark") String remark,
            @Param("createdBy") long createdBy
    );

    @Select("select id from resident where resident_no = #{residentNo}")
    Long findIdByResidentNo(@Param("residentNo") String residentNo);

    @Update("""
            <script>
            update resident
            <set>
              household_id = #{householdId},
              real_name = #{realName},
              gender = #{gender},
              birth_date = #{birthDate},
              address = #{address},
              is_householder = #{householder},
              special_group_tags = cast(#{specialGroupTags} as json),
              remark = #{remark},
              <if test="idCardCiphertext != null">
                id_card_ciphertext = #{idCardCiphertext},
                id_card_hash = #{idCardHash},
                id_card_last4 = #{idCardLast4},
              </if>
              <if test="phoneCiphertext != null">
                phone_ciphertext = #{phoneCiphertext},
                phone_hash = #{phoneHash},
                phone_last4 = #{phoneLast4},
              </if>
              version = version + 1
            </set>
            where id = #{id} and version = #{version}
            </script>
            """)
    int update(
            @Param("id") long id,
            @Param("householdId") Long householdId,
            @Param("realName") String realName,
            @Param("gender") String gender,
            @Param("birthDate") LocalDate birthDate,
            @Param("idCardCiphertext") byte[] idCardCiphertext,
            @Param("idCardHash") String idCardHash,
            @Param("idCardLast4") String idCardLast4,
            @Param("phoneCiphertext") byte[] phoneCiphertext,
            @Param("phoneHash") String phoneHash,
            @Param("phoneLast4") String phoneLast4,
            @Param("address") String address,
            @Param("householder") boolean householder,
            @Param("specialGroupTags") String specialGroupTags,
            @Param("remark") String remark,
            @Param("version") int version
    );

    @Update("""
            update resident set status = #{status}, version = version + 1
            where id = #{id} and version = #{version}
            """)
    int updateStatus(
            @Param("id") long id,
            @Param("status") String status,
            @Param("version") int version
    );

    @Select("""
            select count(*) from resident
            where id_card_hash = #{hash} and id <> #{excludedId}
            """)
    int countByIdCardHash(
            @Param("hash") String hash,
            @Param("excludedId") long excludedId
    );

    @Select("""
            select id, grid_id as gridId,
                   id_card_ciphertext as idCardCiphertext,
                   phone_ciphertext as phoneCiphertext
            from resident
            where id = #{id}
            """)
    Optional<ResidentSensitiveRow> findSensitiveById(@Param("id") long id);

    @Select("""
            select count(*) from resident
            where household_id = #{householdId}
              and is_householder = 1
              and status = 'ACTIVE'
              and id <> #{excludedId}
            """)
    int countOtherActiveHouseholders(
            @Param("householdId") long householdId,
            @Param("excludedId") long excludedId
    );

    record ResidentRow(
            Long id,
            String residentNo,
            Long gridId,
            String gridName,
            Long householdId,
            String householdNo,
            String realName,
            String gender,
            LocalDate birthDate,
            String idCardLast4,
            String phoneLast4,
            String address,
            boolean householder,
            String specialGroupTags,
            String remark,
            String status,
            int version
    ) {
    }

    record ResidentSensitiveRow(
            Long id,
            Long gridId,
            byte[] idCardCiphertext,
            byte[] phoneCiphertext
    ) {
    }
}
