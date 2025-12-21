FROM postgres:16-alpine

COPY src/main/resources/db/tsearch_data/custom_thesaurus.ths \
     /usr/local/share/postgresql/tsearch_data/custom_thesaurus.ths

RUN chmod 644 /usr/local/share/postgresql/tsearch_data/custom_thesaurus.ths
