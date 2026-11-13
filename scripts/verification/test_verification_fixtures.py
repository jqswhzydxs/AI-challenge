import datetime as dt
import unittest
from verification_fixtures import choose_date, ensure_window


class RepeatableFixturesTest(unittest.TestCase):
    def test_allocates_beyond_old_exhausted_pool(self):
        used={(dt.date(2026,7,20)+dt.timedelta(days=i)).isoformat() for i in range(26)}
        self.assertEqual(dt.date(2026,11,12),choose_date(used,dt.date(2026,11,13)))
        used.add('2026-11-12')
        self.assertEqual(dt.date(2026,11,11),choose_date(used,dt.date(2026,11,13)))

    def test_existing_timestamp_is_never_rewritten(self):
        commands=[]
        def sql(query):
            commands.append(query)
            return '2026-11-05 00:00:00' if query.startswith('SELECT') else ''
        self.assertEqual(671,ensure_window(sql,dt.date(2026,11,12)))
        inserts=''.join(commands[1:])
        self.assertNotIn('2026-11-05 00:00:00',inserts)
        self.assertNotIn('UPDATE',inserts)
        self.assertNotIn('DELETE',inserts)
        self.assertIn('VERIFICATION_SYNTHETIC',inserts)


if __name__ == '__main__': unittest.main()
