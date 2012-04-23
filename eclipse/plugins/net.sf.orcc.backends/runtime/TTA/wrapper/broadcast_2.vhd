-------------------------------------------------------------------------------
-- Title      : Broadcast
-- Project    : Orcc - TTA backend
-------------------------------------------------------------------------------
-- File       : broadcast.vhd
-- Author     : hyviquel  <herve.yviquel@irisa.fr>
-- Company    : IRISA
-- Created    : 2012-03-14
-- Last update: 2012-04-23
-- Platform   : All
-- Standard   : VHDL'93
-------------------------------------------------------------------------------
-- Description: Implementation of a generic hardware broadcast
-------------------------------------------------------------------------------
-- Copyright (c) 2012 
-------------------------------------------------------------------------------
-- Revisions  :
-- Date        Version  Author  Description
-- 2012-03-14  0.1      hyviquel        Created
-- 2012-03-20  1.0      hyviquel        First working version
-------------------------------------------------------------------------------

-------------------------------------------------------------------------------
-- Broadcast
-------------------------------------------------------------------------------
library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

library work;
use work.broadcast_type.all;

entity broadcast_2 is

  port (
    clk          : in  std_logic;
    data_0_in    : in  std_logic_vector(31 downto 0);
    status_0_in  : in  std_logic_vector(31 downto 0);
    ack_0_in     : out std_logic;
    data_0_out   : out std_logic_vector(31 downto 0);
    status_0_out : in  std_logic_vector(31 downto 0);
    dv_0_out     : out std_logic;
    data_1_out   : out std_logic_vector(31 downto 0);
    status_1_out : in  std_logic_vector(31 downto 0);
    dv_1_out     : out std_logic;
    rst_n        : in  std_logic
    );

end broadcast_2;


architecture rtl of broadcast_2 is

  signal data_outputs   : array_logic_v32(2-1 downto 0);
  signal status_outputs : array_logic_v32(2-1 downto 0);
  signal dv_outputs     : std_logic_vector(2-1 downto 0);

begin

  data_0_out        <= data_outputs(0);
  dv_0_out          <= dv_outputs(0);
  status_outputs(0) <= status_0_out;
  data_1_out        <= data_outputs(1);
  dv_1_out          <= dv_outputs(1);
  status_outputs(1) <= status_1_out;

  broadcast_inst : entity work.broadcast
    generic map(output_number => 2)
    port map(clk            => clk,
             data_input     => data_0_in,
             status_input   => status_0_in,
             ack_input      => ack_0_in,
             data_outputs   => data_outputs,
             status_outputs => status_outputs,
             dv_outputs     => dv_outputs,
             rstx           => rst_n);



end rtl;

