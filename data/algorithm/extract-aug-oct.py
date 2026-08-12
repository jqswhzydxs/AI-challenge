from collections import Counter
import os, time

src = r'D:\seed\sophomore_second\june\6_26_tiaozhanbei\AI-challenge\data\algorithm\steel_data_1min_pchip.csv'
dst = r'D:\seed\sophomore_second\june\6_26_tiaozhanbei\AI-challenge\data\algorithm\steel_data_1min_pchip_aug_oct.csv'
target_year = 32  # 0032
keep_months = {8, 9, 10}

print(f'Source: {os.path.getsize(src) / 1024 / 1024:.1f} MB', flush=True)
print(f'Extracting {target_year:04d} year months {sorted(keep_months)}...', flush=True)

t0 = time.time()
kept = 0
total = 0
month_counter = Counter()
with open(src, encoding='utf-8') as fin, open(dst, 'w', encoding='utf-8') as fout:
    header = fin.readline()
    fout.write(header)
    for line in fin:
        total += 1
        ts = line.split(',', 1)[0]
        # timestamp format: yyyy-MM-dd HH:mm:ss
        if len(ts) < 7:
            continue
        try:
            year = int(ts[:4])
            month = int(ts[5:7])
        except ValueError:
            continue
        month_counter[month] += 1
        if year == target_year and month in keep_months:
            fout.write(line)
            kept += 1

elapsed = time.time() - t0
print(f'Processed {total} rows in {elapsed:.1f}s', flush=True)
print(f'Kept {kept} rows', flush=True)
print(f'Month distribution: {dict(sorted(month_counter.items()))}', flush=True)
print(f'Output: {dst} ({os.path.getsize(dst) / 1024 / 1024:.1f} MB)', flush=True)
