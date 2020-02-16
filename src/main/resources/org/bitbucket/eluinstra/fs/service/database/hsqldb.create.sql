--
-- Copyright 2020 E.Luinstra
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE fs_client
(
	id								INTEGER					GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	name							VARCHAR(256)		NOT NULL UNIQUE,
	certificate				BLOB						NOT NULL
);

CREATE TABLE fs_file
(
	virtual_path			VARCHAR(256)		NOT NULL PRIMARY KEY,
	real_path					VARCHAR(256)		NOT NULL,
	content_type			VARCHAR(256)		NOT NULL,
	timestamp					TIMESTAMP				DEFAULT NOW NOT NULL,
	md5_checksum			VARCHAR(32)			NOT NULL,
	sha256_checksum		VARCHAR(64)			NOT NULL,
	start_date				TIMESTAMP				NOT NULL,
	end_date					TIMESTAMP				NOT NULL,
	client_id					INTEGER					NOT NULL,
	FOREIGN KEY (client_id) REFERENCES fs_client(id)
);
