package com.xq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xq.model.entity.MpcRealtimeControl;
import com.xq.model.vo.RealtimeControlVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MpcRealtimeControlMapper extends BaseMapper<MpcRealtimeControl> {

    @Select("""
            SELECT
              id AS control_id,
              DATE_FORMAT(control_date, '%Y-%m-%d') AS control_date,
              TIME_FORMAT(control_time, '%H:%i:%s') AS timestamp,
              boiler_load_mw,
              turbine_output_mw,
              grid_purchase_kwh,
              power_factor_target,
              elec_next_5min_kwh,
              steam_next_5min_t,
              DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time
            FROM mpc_realtime_control
            ORDER BY control_date DESC, control_time DESC, id DESC
            LIMIT 1
            """)
    @Results(id = "RealtimeControlVOMap", value = {
            @Result(property = "controlId", column = "control_id"),
            @Result(property = "controlDate", column = "control_date"),
            @Result(property = "timestamp", column = "timestamp"),
            @Result(property = "boilerLoad", column = "boiler_load_mw"),
            @Result(property = "turbineOutput", column = "turbine_output_mw"),
            @Result(property = "gridPurchase", column = "grid_purchase_kwh"),
            @Result(property = "powerFactorTarget", column = "power_factor_target"),
            @Result(property = "elecNext5min", column = "elec_next_5min_kwh"),
            @Result(property = "steamNext5min", column = "steam_next_5min_t"),
            @Result(property = "createTime", column = "create_time")
    })
    RealtimeControlVO selectLatestVO();

    @Select("""
            <script>
            SELECT
              id AS control_id,
              DATE_FORMAT(control_date, '%Y-%m-%d') AS control_date,
              TIME_FORMAT(control_time, '%H:%i:%s') AS timestamp,
              boiler_load_mw,
              turbine_output_mw,
              grid_purchase_kwh,
              power_factor_target,
              elec_next_5min_kwh,
              steam_next_5min_t,
              DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time
            FROM mpc_realtime_control
            <where>
              <if test="startDate != null and startDate != ''">
                control_date &gt;= #{startDate}
              </if>
              <if test="endDate != null and endDate != ''">
                AND control_date &lt;= #{endDate}
              </if>
            </where>
            ORDER BY control_date DESC, control_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    @Results(id = "RealtimeControlVOHistoryMap", value = {
            @Result(property = "controlId", column = "control_id"),
            @Result(property = "controlDate", column = "control_date"),
            @Result(property = "timestamp", column = "timestamp"),
            @Result(property = "boilerLoad", column = "boiler_load_mw"),
            @Result(property = "turbineOutput", column = "turbine_output_mw"),
            @Result(property = "gridPurchase", column = "grid_purchase_kwh"),
            @Result(property = "powerFactorTarget", column = "power_factor_target"),
            @Result(property = "elecNext5min", column = "elec_next_5min_kwh"),
            @Result(property = "steamNext5min", column = "steam_next_5min_t"),
            @Result(property = "createTime", column = "create_time")
    })
    List<RealtimeControlVO> selectHistoryVO(@Param("startDate") String startDate,
                                            @Param("endDate") String endDate,
                                            @Param("offset") long offset,
                                            @Param("pageSize") long pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM mpc_realtime_control
            <where>
              <if test="startDate != null and startDate != ''">
                control_date &gt;= #{startDate}
              </if>
              <if test="endDate != null and endDate != ''">
                AND control_date &lt;= #{endDate}
              </if>
            </where>
            </script>
            """)
    long countHistory(@Param("startDate") String startDate,
                      @Param("endDate") String endDate);
}
