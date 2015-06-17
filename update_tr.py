#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Created on Fri Mar 13 14:20:20 2015

 Kontalk Java client
 Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.


Find all translation strings in Java code and update the strings property file.

Usage: $python update_tr.py src/main/resources/res/i18n/strings.properties src/main/java/org/kontalk/view

@author: Alexander Bikadorov
"""

import sys
import argparse
import logging
import os
import collections
import fnmatch
import re
import string
import random


def _read_file(file_):
    """Read content of file
       file_: either path to file or a file (like) object.
       Return content of file as string or 'None' if file does not exist.
    """
    if type(file_) is str:
        try:
            file_ = open(file_, 'r')
        except IOError:
            logging.warning('file does not exist (can not read): ' + file_)
            return None
        cont_str = file_.read()
        file_.close()
    else:
        cont_str = file_.read()
    return cont_str


def _read_file_lines(file_):
    """Read lines of file
       file_: either path to file or a file (like) object.
       Return list of lines read or 'None' if file does not exist.
    """
    cont_str = _read_file(file_)
    if cont_str is None:
        return None
    return [url_str.rstrip() for url_str in cont_str.splitlines()]


def _read_properties(prop_file):
    """Read Java property file. Comments/empty lines are ignored!
       Return an ordered dictionary with key/values or 'None' if file does not exist.
    """
    lines = _read_file_lines(prop_file)
    if lines is None:
        return None
    splits = (tuple(l.split('=', 1))
              for l in lines if len(l) > 3 and not l.strip().startswith('#'))
    return collections.OrderedDict((k.strip(), v.strip()) for k, v in (t for t in splits if len(t) == 2))


def _find_files(dir_, regex='*.*'):
    """Walk recursively through all dirs in 'dir_'.
       Yield all files in 'dir_' matching 'regex' with absolute path
    """
    abs_path = os.path.abspath(dir_)
    if not os.path.isdir(abs_path):
        logging.warning('does not exist/is not a directory: ' + abs_path)
    for root, dirnames, filenames in os.walk(abs_path):
        for filename in fnmatch.filter(filenames, regex):
            yield os.path.join(root, filename)

re = re.compile('Tr\.tr\("(.+?)"\)')
def _get_tr_strings(file_):
    return re.findall(_read_file(file_))


def _rand_str(n, chars=string.ascii_uppercase + string.digits):
    """Get a string of 'n' random characters choosen out of 'chars'"""
    return ''.join(random.choice(chars) for _ in range(n))


def _write_file_OVERWRITE(file_path_str, str_):
    """Write string to file."""
    dir_name, fname = os.path.split(file_path_str)
    if not os.path.isfile(file_path_str):
        logging.warning(
            'file ' + file_path_str + ' does not exist, not overwriting')
        return False
    file_ = open(file_path_str, 'w')
    file_.write(str_)
    file_.close()
    logging.info(
        "wrote " + str(len(str_)) + " bytes to file: " + file_path_str)
    return True


def _arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument("-i", "--init", action="store_true", default=False,
                        help='initialize (ignore if properties file does not exist)')
    parser.add_argument(
        "strings_file", type=str, help="string properties file to update")
    parser.add_argument(
        "source_dir", type=str, help="base directory to seach in for Java source files")
    return parser.parse_args()


def main(argv=sys.argv):
    logging.getLogger().setLevel(logging.INFO)

    # read strings file
    args = _arguments()
    strings_dict = _read_properties(args.strings_file)
    if strings_dict is None:
        if not args.init:
            logging.warning('no property file, abort')
            return 1
        strings_dict = {}

    # find all translation strings in Java code
    tr_string_list = []
    for j_file in _find_files(args.source_dir, '*.java'):
        strings = _get_tr_strings(j_file)
        tr_string_list += strings
        #print('file: '+j_file)
        # print('>>>>'+'\n>>>>'.join(strings))

    if not tr_string_list:
        logging.warning('no source strings found, abort')
        return 2

    # first, take care of strings that did not change to preserve order
    # and ignore unused strings
    upd_dict = collections.OrderedDict()
    for prop_key, str_ in strings_dict.items():
        if str_ in tr_string_list:
            upd_dict[prop_key] = str_
        else:
            logging.info('removing unused string: "' + str_ + '"')

    # add all new strings
    old_strings = strings_dict.values()
    for str_ in (s for s in tr_string_list if s not in old_strings):
        logging.info('adding new string: "' + str_ + '"')
        upd_dict['s_' + _rand_str(4)] = str_

    write_str = '\n' + \
        '\n'.join(v + " = " + k for v, k in upd_dict.items()) + '\n'
    if strings_dict == upd_dict:
        logging.info('no changes detected')
    else:
        if not _write_file_OVERWRITE(args.strings_file, write_str):
            logging.warning('could not write output, abort')
            return 3

    logging.info("Update successful")

if __name__ == "__main__":
    sys.exit(main())
