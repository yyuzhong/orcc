-------------------------------------------------------------------------------
-- Title      : Gray to Binary
-- Project    : ORCC
-------------------------------------------------------------------------------
-- File       : Gray2bin.vhd
-- Author     : Nicolas Siret (nicolas.siret@ltdsa.com)
-- Company    : Lead Tech Design
-- Created    : 
-- Last update: 2010-07-01
-- Platform   : 
-- Standard   : VHDL'93
-------------------------------------------------------------------------------
-- Copyright (c) 2009-2010, LEAD TECH DESIGN Rennes - France
-- Copyright (c) 2009-2010, IETR/INSA of Rennes
-- All rights reserved.
-- 
-- Redistribution and use in source and binary forms, with or without
-- modification, are permitted provided that the following conditions are met:
-- 
--  -- Redistributions of source code must retain the above copyright notice,
--     this list of conditions and the following disclaimer.
--  -- Redistributions in binary form must reproduce the above copyright notice,
--     this list of conditions and the following disclaimer in the documentation
--     and/or other materials provided with the distribution.
--  -- Neither the name of the LEAD TECH DESIGN and INSA/IETR nor the names of its
--     contributors may be used to endorse or promote products derived from this
--     software without specific prior written permission.
-- 
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
-- AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
-- IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
-- ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
-- LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
-- CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
-- SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
-- INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
-- STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
-- WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
-- SUCH DAMAGE.
-------------------------------------------------------------------------------
-- Revisions  :
-- Date        Version  Author       Description
-- 2010-02-09  1.0      Nicolas      Created
-------------------------------------------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;
library work;
use work.orcc_package.all;
-------------------------------------------------------------------------------

entity Gray2bin is
  generic (
    depth : integer := 32);
  port (
    rd_add_gray : in    std_logic_vector(bit_width(depth)-1 downto 0);
    wr_add_gray : in    std_logic_vector(bit_width(depth)-1 downto 0);
    --
    rd_add_bin  : inout std_logic_vector(bit_width(depth)-1 downto 0);
    wr_add_bin  : inout std_logic_vector(bit_width(depth)-1 downto 0));
end Gray2bin;

-------------------------------------------------------------------------------

architecture archGray2bin of Gray2bin is
begin

  rd_add_bin(bit_width(depth)-1) <= rd_add_gray(bit_width(depth)-1);
  create_rd_lsb : for i in 1 to bit_width(depth)-1 generate
    rd_add_bin(i -1) <= rd_add_bin(i) xor rd_add_gray(i -1);
  end generate;
  --
  wr_add_bin(bit_width(depth)-1) <= wr_add_gray(bit_width(depth)-1);
  create_wr_lsb : for i in 1 to bit_width(depth)-1 generate
    wr_add_bin(i -1) <= wr_add_bin(i) xor wr_add_gray(i -1);
  end generate;
  
end archGray2bin;

