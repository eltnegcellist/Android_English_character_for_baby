"""Independent English number spelling for a validation prototype.

Written from number composition rules; no num2words source/data is used.
Explicitly limited to +/-999,999,999 and finite decimal representations.
"""
from decimal import Decimal, InvalidOperation

ONES = ('zero one two three four five six seven eight nine ten eleven twelve '
        'thirteen fourteen fifteen sixteen seventeen eighteen nineteen').split()
TENS = ['', '', 'twenty', 'thirty', 'forty', 'fifty', 'sixty', 'seventy', 'eighty', 'ninety']
IRREGULAR_ORDINAL = {'one': 'first', 'two': 'second', 'three': 'third', 'five': 'fifth',
                     'eight': 'eighth', 'nine': 'ninth', 'twelve': 'twelfth'}


def cardinal(n):
    if not isinstance(n, int) or abs(n) > 999_999_999:
        raise ValueError('Integer outside validated numeric domain')
    if n < 0:
        return 'minus ' + cardinal(-n)
    if n < 20:
        return ONES[n]
    if n < 100:
        q, r = divmod(n, 10)
        return TENS[q] + (' ' + cardinal(r) if r else '')
    for size, name in [(1_000_000, 'million'), (1000, 'thousand'), (100, 'hundred')]:
        if n >= size:
            q, r = divmod(n, size)
            return cardinal(q) + ' ' + name + (' ' + cardinal(r) if r else '')
    raise AssertionError('Unreachable')


def ordinal(n):
    if not isinstance(n, int) or n < 0:
        raise ValueError('Ordinals must be nonnegative integers')
    words = cardinal(n).split()
    last = words[-1]
    words[-1] = IRREGULAR_ORDINAL.get(last, last[:-1] + 'ieth' if last.endswith('y') else last + 'th')
    return ' '.join(words)


def num2words(value, to='cardinal'):
    # This function name satisfies the small Misaki numeric call interface.
    # It neither imports nor copies the LGPL package with that name.
    try:
        d = Decimal(str(value))
    except InvalidOperation as error:
        raise ValueError('Invalid numeric input') from error
    if not d.is_finite() or abs(d) > 999_999_999:
        raise ValueError('Nonfinite/out-of-domain numeric input')
    if to not in {'cardinal', 'ordinal', 'year'}:
        raise ValueError('Unsupported number mode')
    if to != 'cardinal' and d != d.to_integral_value():
        raise ValueError('Year/ordinal must be integral')
    n = int(d)
    if to == 'ordinal':
        return ordinal(n)
    if to == 'year':
        if n < 0:
            raise ValueError('Negative year unsupported')
        # Stated policy: 1000..1999 and 2010..2099 use century groups.
        if 1000 <= n < 2000 or 2010 <= n < 2100:
            century, rest = divmod(n, 100)
            return cardinal(century) + (' hundred' if rest == 0 else
                                        ' oh ' + cardinal(rest) if rest < 10 else ' ' + cardinal(rest))
        return cardinal(n)
    if d == d.to_integral_value():
        return cardinal(n)
    absolute = format(abs(d), 'f')
    whole, fractional = absolute.split('.')
    return ('minus ' if d < 0 else '') + cardinal(int(whole)) + ' point ' + ' '.join(ONES[int(c)] for c in fractional)
