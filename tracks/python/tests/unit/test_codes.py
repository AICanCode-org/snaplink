from app.codes import ALPHABET, CODE_LENGTH, generate_code


def test_code_has_expected_length_and_alphabet():
    code = generate_code()
    assert len(code) == CODE_LENGTH == 7
    assert set(code) <= set(ALPHABET)


def test_alphabet_is_base62():
    assert len(ALPHABET) == 62 and len(set(ALPHABET)) == 62


def test_codes_are_random_enough():
    codes = {generate_code() for _ in range(2000)}
    assert len(codes) == 2000  # a collision here would be astronomically unlikely
