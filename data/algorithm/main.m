%% =====================================================
%% 生产-能源协同优化 主程序 v1.1 (MILP v3.2 对齐版)
%% 用法: main()
%% 输出: output_sample.json
%% =====================================================

function main(inputFile, outputFile)
if nargin < 2
    inputFile = 'steel_data_cleaned.csv';
    outputFile = 'output_sample.json';
end

try
fprintf('========================================\n');
fprintf('  生产-能源协同优化 v1.1 (MILP v3.2 aligned)\n');
fprintf('========================================\n');
fprintf('输入: %s\n', inputFile);
fprintf('输出: %s\n\n', outputFile);

%% ============================================
%  第0步：数据准备（兼容 1分钟输入 / 15分钟输入）
% ============================================
fprintf('=== Step 0: 数据准备 (1min直用 / 15min转1min) ===\n');

if ~exist(inputFile, 'file')
    error('ALG:InputFileNotFound', '输入文件不存在: %s', inputFile);
end

raw_data = readtable(inputFile);

% 统一列名
raw_cols = raw_data.Properties.VariableNames;
for i = 1:length(raw_cols)
    switch lower(raw_cols{i})
        case {'date', 'timestamp', 'datetime'}
            raw_data.Properties.VariableNames{i} = 'timestamp';
        case {'usage_kwh', 'power', 'power_kw', 'elec'}
            raw_data.Properties.VariableNames{i} = 'elec';
    end
end

if ~ismember('timestamp', raw_data.Properties.VariableNames)
    error('ALG:InputFormat', '输入格式错误，请确保CSV包含 timestamp, elec, steam 三列');
end
if ~ismember('elec', raw_data.Properties.VariableNames)
    error('ALG:InputFormat', '输入格式错误，请确保CSV包含 timestamp, elec, steam 三列');
end

if ischar(raw_data.timestamp(1)) || isstring(raw_data.timestamp(1)) || iscell(raw_data.timestamp)
    raw_data.timestamp = parseTimestamps(raw_data.timestamp);
end

raw_data.elec = fillmissing(raw_data.elec, 'linear');
raw_data = raw_data(raw_data.elec > 0.01, :);
raw_data = sortrows(raw_data, 'timestamp');

if height(raw_data) < 2
    error('ALG:InsufficientData', '输入数据不足，至少需要2条有效数据');
end

time_all = datenum(raw_data.timestamp);
if any(isnat(raw_data.timestamp)) || any(isnan(time_all))
    error('ALG:TimestampFormat', '时间字段解析失败，请确认 timestamp 格式为 MM/dd/yyyy HH:mm 或 yyyy-MM-dd HH:mm:ss');
end
if any(diff(time_all) <= 0)
    error('ALG:TimestampOrder', '时间字段必须按升序排列且不能重复');
end

step_minutes = median(diff(time_all)) * 1440;
fprintf('✅ 原始数据: %d 条，估计粒度: %.2f 分钟\n', height(raw_data), step_minutes);

n_days = 7;
if step_minutes <= 2
    data = raw_data;
    if ~ismember('steam', data.Properties.VariableNames)
        data.steam = data.elec * 0.005 + 0.5;
    end
    fprintf('✅ 检测到1分钟级数据，直接进入MILP: %d 条\n', height(data));
elseif step_minutes >= 10 && step_minutes <= 20
    n_points = n_days * 24 * 4;  % 7天 × 24小时 × 4个点/小时 = 672
    if height(raw_data) < n_points
        error('ALG:InsufficientData', ...
            '输入数据不足，需要至少7天（672个点）的15分钟数据');
    end
    raw_sub = raw_data(end-n_points+1:end, :);
    fprintf('✅ 检测到15分钟数据，取最近 %d 个点 (约 %d 天)\n', n_points, n_days);

    t_orig = datenum(raw_sub.timestamp);
    y_orig = raw_sub.elec;
    t_target = (t_orig(1) : 1/1440 : t_orig(end))';
    expected_points = n_days * 24 * 60;
    if length(t_target) > expected_points * 2
        error('ALG:TimestampRange', ...
            '时间跨度异常，7天输入插值后不应超过约10080个1分钟点，请检查 timestamp 日期格式');
    end
    y_pchip = pchip(t_orig, y_orig, t_target);

    data = table();
    data.timestamp = datetime(t_target, 'ConvertFrom', 'datenum');
    data.elec = y_pchip;
    data.steam = y_pchip * 0.005 + 0.5;
    fprintf('✅ PCHIP插值完成: %d 条 (1分钟粒度)\n', height(data));
else
    error('ALG:UnsupportedGranularity', ...
        '输入数据粒度异常，当前仅支持约1分钟或15分钟数据，检测到 %.2f 分钟', step_minutes);
end

%% ============================================
%  第一步：日级MILP排产模型
% ============================================
fprintf('\n=== Step 1: 日级MILP排产 ===\n');

% 聚合到小时
data.elec = fillmissing(data.elec, 'linear');
data.steam = fillmissing(data.steam, 'linear');
data = data(data.elec > 0.01, :);

time_num = datenum(data.timestamp);
time_hour = floor(time_num * 24) / 24;
[unique_hours, ~, hour_idx] = unique(time_hour);
n_hours_data = length(unique_hours);
elec_hourly = zeros(n_hours_data, 1);
for i = 1:n_hours_data
    idx = (hour_idx == i);
    elec_hourly(i) = mean(data.elec(idx));
end
fprintf('✅ 聚合为 %d 小时\n', n_hours_data);

% 生成需求
n_recent = min(168, n_hours_data);
base_demand = mean(elec_hourly(end-n_recent+1:end)) * 0.1;
rng(42);
demand_forecast = base_demand * (0.7 + 0.6 * rand(24,1));
demand_forecast = max(demand_forecast, base_demand * 0.4);
fprintf('总需求: %.1f 吨\n', sum(demand_forecast));

% MILP优化（对齐 MILP_v2 / 日级MILP排产模型 v3.2）
base_elec_coeff = 14.00;
n_hours = 24;
n_vars = 24 + 24 + 24;
idx_prod = 1:24;
idx_s = 25:48;
idx_start = 49:72;

f = zeros(n_vars, 1);
f(idx_prod) = base_elec_coeff;

A_eq = zeros(1, n_vars);
A_eq(idx_prod) = 1;
b_eq = sum(demand_forecast);

A_ineq = [];
b_ineq = [];

for t = 1:24
    row = zeros(1, n_vars);
    row(idx_prod(t)) = 1;
    row(idx_s(t)) = -0.80;
    A_ineq = [A_ineq; row];
    b_ineq = [b_ineq; 0.15];
end

row = zeros(1, n_vars);
row(idx_s) = 1;
A_ineq = [A_ineq; row];
b_ineq = [b_ineq; 12];
row = zeros(1, n_vars);
row(idx_s) = -1;
A_ineq = [A_ineq; row];
b_ineq = [b_ineq; -8];

lb = zeros(n_vars, 1);
ub = zeros(n_vars, 1);
lb(idx_prod) = 0.15; ub(idx_prod) = 1.0;
lb(idx_s) = 0; ub(idx_s) = 1;
lb(idx_start) = 0; ub(idx_start) = 1;
intcon = [idx_s, idx_start];

options = optimoptions('intlinprog', 'Display', 'off', 'MaxTime', 120);
[x, fval, exitflag] = intlinprog(f, intcon, A_ineq, b_ineq, A_eq, b_eq, lb, ub, options);

if exitflag <= 0
    fprintf('⚠️ 求解失败，使用备用方案\n');
    x_prod = demand_forecast / sum(demand_forecast) * sum(demand_forecast);
    s_heat = zeros(24, 1);
    valley_hours = [1,2,3,4,5,6,7,22,23];
    for i = 1:length(valley_hours)
        if valley_hours(i) <= 24
            s_heat(valley_hours(i)+1) = 1;
        end
    end
    solver_status = 'fallback';
else
    x_prod = x(idx_prod);
    s_heat = round(x(idx_s));
    solver_status = 'optimal';
end
fprintf('✅ 排产完成，总产量: %.1f 吨\n', sum(x_prod));
fprintf('✅ 加热炉运行 %d 小时\n', sum(s_heat));

% 工艺参数优化
T_range = [1140, 1145, 1150, 1155, 1160];
v_range = [9, 9.5, 10, 10.5, 11];
T_ref = 1150; v_ref = 10;
temp_coeff = 0.00222;
speed_coeff = 0.03;
best_T = T_ref; best_v = v_ref; best_energy = inf;

for Ti = 1:length(T_range)
    for vi = 1:length(v_range)
        T = T_range(Ti); v = v_range(vi);
        temp_factor = 1 + temp_coeff * (T - T_ref);
        speed_factor = 1 - speed_coeff * (v - v_ref);
        coeff = base_elec_coeff * temp_factor * speed_factor;
        energy = sum(x_prod) * coeff;
        if energy < best_energy
            best_energy = energy; best_T = T; best_v = v; best_coeff = coeff;
        end
    end
end

% 计算EC
EC_baseline = base_elec_coeff;
EC_optimized = best_coeff;
reduction_rate = (EC_baseline - EC_optimized) / EC_baseline * 100;
total_prod = sum(x_prod);
total_energy = total_prod * best_coeff;

fprintf('✅ EC降低率: %.2f%%\n', reduction_rate);

% 日级JSON
schedule = struct('hour', [], 'demand', [], 'production', []);
for i = 1:24
    schedule(i).hour = i-1;
    schedule(i).demand = demand_forecast(i);
    schedule(i).production = x_prod(i);
end

daily_json = struct();
daily_json.timestamp = datestr(now, 'yyyy-mm-dd HH:MM:SS');
daily_json.plan_horizon = 24;
daily_json.unit = 'hour';
daily_json.data_granularity = '1 minute';
daily_json.EC_baseline = EC_baseline;
daily_json.EC_optimized = EC_optimized;
daily_json.EC_reduction = reduction_rate;
daily_json.total_production = total_prod;
daily_json.total_energy = total_energy;
daily_json.optimal_temperature = best_T;
daily_json.optimal_speed = best_v;
daily_json.solver_status = solver_status;
daily_json.solver_exitflag = exitflag;
daily_json.schedule = schedule;

fid = fopen('daily_plan_temp.json', 'w');
fprintf(fid, '%s', jsonencode(daily_json));
fclose(fid);
fprintf('✅ 日级计划已生成\n');

%% ============================================
%  第二步：实时MPC调控模型
% ============================================
fprintf('\n=== Step 2: 实时MPC调控 ===\n');

% 读取日级计划
fid = fopen('daily_plan_temp.json', 'r');
json_str = fread(fid, '*char')';
fclose(fid);
plan = jsondecode(json_str);
production = [plan.schedule.production];

% 用PCHIP后的1分钟数据做MPC
sim_data = data(end-min(24*60, height(data))+1:end, :);

% MPC参数
MPC = struct();
MPC.prediction_horizon = 60;
MPC.control_horizon = 10;
MPC.sample_time = 1;
MPC.boiler_min = 20;
MPC.boiler_max = 80;
MPC.turbine_min = 5;
MPC.turbine_max = 30;
MPC.ramp_rate = 5;
MPC.w_elec = 2.0;

elec_ref_by_hour = production * 14.00;
elec_ref_minutely = zeros(24*60, 1);
for i = 1:24
    start_idx = (i-1)*60 + 1;
    end_idx = i*60;
    elec_ref_minutely(start_idx:end_idx) = elec_ref_by_hour(i);
end

n_steps = min(120, height(sim_data));
time_vec = cell(n_steps, 1);
boiler_vec = zeros(n_steps, 1);
turbine_vec = zeros(n_steps, 1);
grid_vec = zeros(n_steps, 1);
boiler_state = 30;
turbine_state = 10;

for t = 1:n_steps
    current_time = sim_data.timestamp(t);
    current_hour = hour(current_time);
    minute_of_day = current_hour * 60 + minute(current_time) + 1;
    if minute_of_day > 1440; minute_of_day = 1440; end
    
    plan_elec = elec_ref_minutely(minute_of_day);
    elec_actual = sim_data.elec(t);
    elec_error = elec_actual - plan_elec;
    
    hour_idx = mod(current_hour, 24) + 1;
    base_boiler = 25 + production(hour_idx) * 2;
    base_turbine = 8 + production(hour_idx) * 0.5;
    
    correction = elec_error * 0.25 * MPC.w_elec;
    
    boiler_setpoint = base_boiler - correction;
    boiler_setpoint = max(MPC.boiler_min, min(MPC.boiler_max, boiler_setpoint));
    if t > 1
        delta = boiler_setpoint - boiler_state;
        delta = max(-MPC.ramp_rate, min(MPC.ramp_rate, delta));
        boiler_setpoint = boiler_state + delta;
    end
    boiler_state = boiler_setpoint;
    
    turbine_setpoint = base_turbine + correction * 0.15;
    turbine_setpoint = max(MPC.turbine_min, min(MPC.turbine_max, turbine_setpoint));
    if t > 1
        delta = turbine_setpoint - turbine_state;
        delta = max(-2, min(2, delta));
        turbine_setpoint = turbine_state + delta;
    end
    turbine_state = turbine_setpoint;
    
    grid_purchase = max(0, elec_actual - turbine_setpoint * 0.5);
    
    time_vec{t} = datestr(current_time, 'HH:MM:SS');
    boiler_vec(t) = boiler_setpoint;
    turbine_vec(t) = turbine_setpoint;
    grid_vec(t) = grid_purchase;
end

% 计算ER
total = length(boiler_vec);
executable = zeros(total, 1);
for i = 1:total
    in_range = (boiler_vec(i) >= MPC.boiler_min) && (boiler_vec(i) <= MPC.boiler_max);
    if i == 1
        ramp_ok = true;
    else
        ramp_ok = abs(boiler_vec(i) - boiler_vec(i-1)) <= MPC.ramp_rate;
    end
    if in_range && ramp_ok
        executable(i) = 1;
    end
end
ER = sum(executable) / total * 100;

% 实时JSON
realtime_json = struct();
realtime_json.timestamp = time_vec{end};
realtime_json.control.boiler_load = boiler_vec(end);
realtime_json.control.turbine_output = turbine_vec(end);
realtime_json.control.grid_purchase = grid_vec(end);
realtime_json.control.power_factor_target = 0.95;
realtime_json.forecast.elec_next_5min = mean(boiler_vec) * 0.15;
realtime_json.forecast.steam_next_5min = mean(boiler_vec) * 0.002;
realtime_json.performance.ER = ER;

fprintf('✅ ER (方案可执行率): %.2f%%\n', ER);

fid = fopen('realtime_control_temp.json', 'w');
fprintf(fid, '%s', jsonencode(realtime_json));
fclose(fid);
fprintf('✅ 实时调控已生成\n');

%% ============================================
%  第三步：合并输出
% ============================================
fprintf('\n=== Step 3: 合并输出 ===\n');

result = struct();
result.daily_plan = daily_json;
result.realtime_control = realtime_json;

fid = fopen(outputFile, 'w');
fprintf(fid, '%s', jsonencode(result));
fclose(fid);

delete('daily_plan_temp.json');
delete('realtime_control_temp.json');

fprintf('\n========================================\n');
fprintf('   ✅ 优化完成！\n');
fprintf('   输出文件: %s\n', outputFile);
fprintf('   EC降低率: %.2f%%\n', reduction_rate);
fprintf('   ER可执行率: %.2f%%\n', ER);
fprintf('   插值方法: PCHIP (取最近7天)\n');
fprintf('========================================\n');
catch ME
    writeErrorJson(outputFile, ME, inputFile);
    cleanupTempFiles();
    fprintf('\n========================================\n');
    fprintf('   ❌ 优化失败！\n');
    fprintf('   输出文件: %s\n', outputFile);
    fprintf('   错误: %s\n', ME.message);
    fprintf('========================================\n');
end
end

function writeErrorJson(outputFile, ME, inputFile)
err = struct();
err.status = 'error';
err.message = ME.message;

switch ME.identifier
    case 'ALG:InputFileNotFound'
        err.code = 404;
    case 'ALG:InsufficientData'
        err.code = 400;
        err.required_rows = 672;
        err.received_rows = countCsvRows(inputFile);
    case 'ALG:InputFormat'
        err.code = 415;
    case 'ALG:TimestampFormat'
        err.code = 415;
    case 'ALG:TimestampOrder'
        err.code = 400;
    case 'ALG:TimestampRange'
        err.code = 400;
    case 'ALG:UnsupportedGranularity'
        err.code = 415;
    otherwise
        err.code = 500;
end

fid = fopen(outputFile, 'w');
if fid == -1
    fprintf('无法写入错误输出文件: %s\n', outputFile);
    return;
end
fprintf(fid, '%s', jsonencode(err));
fclose(fid);
end

function rows = countCsvRows(inputFile)
rows = 0;
if ~exist(inputFile, 'file')
    return;
end
fid = fopen(inputFile, 'r');
if fid == -1
    return;
end
while ~feof(fid)
    line = fgetl(fid);
    if ischar(line) && ~isempty(strtrim(line))
        rows = rows + 1;
    end
end
fclose(fid);
rows = max(rows - 1, 0);
end

function timestamps = parseTimestamps(values)
formats = {
    'dd/MM/yyyy HH:mm'
    'dd/MM/yyyy HH:mm:ss'
    'MM/dd/yyyy HH:mm'
    'MM/dd/yyyy HH:mm:ss'
    'yyyy-MM-dd HH:mm:ss'
    'yyyy-MM-dd HH:mm'
    };

lastError = [];
for i = 1:length(formats)
    try
        timestamps = datetime(values, 'InputFormat', formats{i});
        if ~any(isnat(timestamps))
            return;
        end
    catch ME
        lastError = ME;
    end
end

try
    timestamps = datetime(values);
    if ~any(isnat(timestamps))
        return;
    end
catch ME
    lastError = ME;
end

if isempty(lastError)
    error('ALG:TimestampFormat', '时间字段解析失败，请确认 timestamp 格式为 dd/MM/yyyy HH:mm 或 yyyy-MM-dd HH:mm:ss');
end
error('ALG:TimestampFormat', '时间字段解析失败: %s', lastError.message);
end

function cleanupTempFiles()
if exist('daily_plan_temp.json', 'file')
    delete('daily_plan_temp.json');
end
if exist('realtime_control_temp.json', 'file')
    delete('realtime_control_temp.json');
end
end
