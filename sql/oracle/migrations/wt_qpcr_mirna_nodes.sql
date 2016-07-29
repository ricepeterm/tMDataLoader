--
-- Type: TABLE; Owner: TM_DATALOADER; Name: WT_QPCR_MIRNA_NODES
--
 CREATE TABLE "TM_DATALOADER"."WT_QPCR_MIRNA_NODES" 
  (	"LEAF_NODE" VARCHAR2(2000 BYTE), 
"CATEGORY_CD" VARCHAR2(2000 BYTE), 
"PLATFORM" VARCHAR2(2000 BYTE), 
"TISSUE_TYPE" VARCHAR2(2000 BYTE), 
"ATTRIBUTE_1" VARCHAR2(2000 BYTE), 
"ATTRIBUTE_2" VARCHAR2(2000 BYTE), 
"TITLE" VARCHAR2(2000 BYTE), 
"NODE_NAME" VARCHAR2(2000 BYTE), 
"CONCEPT_CD" VARCHAR2(100 BYTE), 
"TRANSFORM_METHOD" VARCHAR2(2000 BYTE), 
"NODE_TYPE" VARCHAR2(50 BYTE)
  ) SEGMENT CREATION IMMEDIATE
 TABLESPACE "TRANSMART" ;

