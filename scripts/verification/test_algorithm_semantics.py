import importlib.util
import sys
import unittest
from pathlib import Path

script=Path(__file__).resolve().parents[2]/'data/algorithm/generate_plan.py'
spec=importlib.util.spec_from_file_location('verified_algorithm',script)
algorithm=importlib.util.module_from_spec(spec)
sys.modules[spec.name]=algorithm
spec.loader.exec_module(algorithm)


class AlgorithmSemanticsTest(unittest.TestCase):
    def test_positive_output_explains_repeated_boundary_solution(self):
        for total in (1,6,24):
            temperature,speed,energy,coefficient=algorithm.optimize_process_parameters(total)
            self.assertEqual((1140,11),(temperature,speed))
            self.assertAlmostEqual(14*(1+.00222*(1140-1150))*(1-.03*(11-10)),coefficient)
            self.assertAlmostEqual(total*coefficient,energy)

    def test_search_status_does_not_claim_global_or_fallback_feasibility(self):
        _,_,status=algorithm.solve_schedule([.2]*24)
        self.assertEqual('feasible_heuristic',status)
        _,_,status=algorithm.solve_schedule([100]*24)
        self.assertEqual('fallback',status)

    def test_zero_production_keeps_original_tie_breaking(self):
        temperature,speed,energy,_=algorithm.optimize_process_parameters(0)
        self.assertEqual((1140,9,0),(temperature,speed,energy))


if __name__=='__main__':unittest.main()
