from scipy.interpolate import lagrange
import itertools
import sys


def check_score(poly, xy_dict):
	score = 0
	xs = list(xy_dict.keys())
	for x in xs:
		if poly(x) == xy_dict[x]:
			score += 1
	return score


def robust_lagrange_interpolation(xy_dict, degree):
	xs = xy_dict.keys()
	permutations = list(itertools.permutations(xs, degree + 1))
	best_score = 0
	best_poly = None
	for perm in permutations:
		curr_ys = []
		for x in perm:
			curr_ys.append(xy_dict[x])
		tmp = list(perm)
		curr_poly = lagrange(tmp, curr_ys)
		curr_score = check_score(curr_poly, xy_dict)
		if curr_score > best_score:
			best_score = curr_score
			best_poly = curr_poly
	return str(best_poly(0))


def parse_input(inp):
	inp_arr = inp.split(',')
	xy_dict = dict()
	for idx, val in enumerate(inp_arr):
		if val != "null":
			xy_dict[int(idx) + 1] = float(val)
	return xy_dict


if __name__ == '__main__':
	if len(sys.argv) == 3:
		values = sys.argv[1]
		poly_deg = int(sys.argv[2])
		xy_dict = parse_input(values)
		out = robust_lagrange_interpolation(xy_dict, poly_deg)
		print(out)

