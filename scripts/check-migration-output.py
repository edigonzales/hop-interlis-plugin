#!/usr/bin/env python3
"""Semantic checks on the real installed-plugin migration result."""
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
assert root.find('.//{http://www.interlis.ch/xtf/2.4/MigrationDemo_V1}Line') is None
for element in root.iter():
    element.tag = element.tag.rsplit('}', 1)[-1]
    element.attrib = {key.rsplit('}', 1)[-1]: value for key, value in element.attrib.items()}
line = root.find('.//Line')
assert line is not None
assert line.attrib['tid'] == 'l1'
assert line.findtext('Name') == 'First'
assert line.findtext('Origin') == 'migration'
assert line.findtext('Status') == 'operational'
assert line.find('Facility').attrib['ref'] == 'f1'
assert float(line.findtext('Location/coord/c3')) == 3
assert line.find('.//arc') is not None
controls = line.findall('Controls/Control')
assert [child.findtext('Office') for child in controls] == ['Second', 'First', 'Second']
assert all(len(child.findall('Details/Detail')) == 2 for child in controls)
