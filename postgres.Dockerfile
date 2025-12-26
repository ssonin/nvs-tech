FROM pgvector/pgvector:pg16

COPY src/main/resources/db/tsearch_data/custom_thesaurus.ths \
     /usr/share/postgresql/16/tsearch_data/custom_thesaurus.ths

RUN chmod 644 /usr/share/postgresql/16/tsearch_data/custom_thesaurus.ths
