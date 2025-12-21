CREATE TEXT SEARCH DICTIONARY custom_thesaurus (
  TEMPLATE = thesaurus,
  DictFile = 'custom_thesaurus',
  Dictionary = pg_catalog.simple
);

ALTER TEXT SEARCH CONFIGURATION english
  ALTER MAPPING FOR asciiword
    WITH custom_thesaurus, english_stem;
