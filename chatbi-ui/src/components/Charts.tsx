import React from 'react';
import ReactECharts from 'echarts-for-react';
import { EChartsOption } from 'echarts';

interface ChartData {
  x: string | number;
  y: number;
  series?: string;
}

interface LineChartProps {
  data: ChartData[];
  height?: number;
  xField?: string;
  yField?: string;
  seriesField?: string;
}

interface ColumnChartProps {
  data: ChartData[];
  height?: number;
  xField?: string;
  yField?: string;
  seriesField?: string;
}

interface BarChartProps {
  data: ChartData[];
  height?: number;
  xField?: string;
  yField?: string;
  seriesField?: string;
}

interface PieChartProps {
  data: Array<{ type: string; value: number; series?: string }>;
  height?: number;
  angleField?: string;
  colorField?: string;
}

export const Line: React.FC<LineChartProps> = ({ 
  data, 
  height = 320, 
  xField = 'x', 
  yField = 'y', 
  seriesField 
}) => {
  const option: EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    legend: seriesField ? {
      data: [...new Set(data.map(d => d.series).filter(Boolean))] as string[]
    } : undefined,
    xAxis: {
      type: 'category',
      data: [...new Set(data.map(d => d.x))],
      axisLabel: {
        rotate: 45
      }
    },
    yAxis: {
      type: 'value'
    },
    series: seriesField ? 
      [...new Set(data.map(d => d.series).filter(Boolean))].map(seriesName => ({
        name: seriesName,
        type: 'line',
        data: data.filter(d => d.series === seriesName).map(d => d.y),
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {
          width: 2
        }
      })) :
      [{
        type: 'line',
        data: data.map(d => d.y),
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {
          width: 2
        }
      }]
  };

  return <ReactECharts option={option} style={{ height: `${height}px` }} />;
};

export const Column: React.FC<ColumnChartProps> = ({ 
  data, 
  height = 320, 
  xField = 'x', 
  yField = 'y', 
  seriesField 
}) => {
  const option: EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    legend: seriesField ? {
      data: [...new Set(data.map(d => d.series).filter(Boolean))] as string[]
    } : undefined,
    xAxis: {
      type: 'category',
      data: [...new Set(data.map(d => d.x))],
      axisLabel: {
        rotate: 45
      }
    },
    yAxis: {
      type: 'value'
    },
    series: seriesField ?
      [...new Set(data.map(d => d.series).filter(Boolean))].map(seriesName => ({
        name: seriesName,
        type: 'bar',
        data: data.filter(d => d.series === seriesName).map(d => d.y),
        barWidth: '60%'
      })) :
      [{
        type: 'bar',
        data: data.map(d => d.y),
        barWidth: '60%'
      }]
  };

  return <ReactECharts option={option} style={{ height: `${height}px` }} />;
};

export const Bar: React.FC<BarChartProps> = ({ 
  data, 
  height = 320, 
  xField = 'x', 
  yField = 'y', 
  seriesField 
}) => {
  const option: EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    legend: seriesField ? {
      data: [...new Set(data.map(d => d.series).filter(Boolean))] as string[]
    } : undefined,
    xAxis: {
      type: 'value'
    },
    yAxis: {
      type: 'category',
      data: [...new Set(data.map(d => d.x))],
      axisLabel: {
        rotate: 0
      }
    },
    series: seriesField ?
      [...new Set(data.map(d => d.series).filter(Boolean))].map(seriesName => ({
        name: seriesName,
        type: 'bar',
        data: data.filter(d => d.series === seriesName).map(d => d.y),
        barWidth: '60%'
      })) :
      [{
        type: 'bar',
        data: data.map(d => d.y),
        barWidth: '60%'
      }]
  };

  return <ReactECharts option={option} style={{ height: `${height}px` }} />;
};

export const Pie: React.FC<PieChartProps> = ({ 
  data, 
  height = 320, 
  angleField = 'value', 
  colorField = 'type' 
}) => {
  const option: EChartsOption = {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left',
      data: data.map(d => d.type)
    },
    series: [{
      name: '数据',
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '50%'],
      data: data.map(d => ({
        name: d.type,
        value: d.value
      })),
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      },
      label: {
        show: true,
        fontSize: 12,
        formatter: '{b}: {d}%'
      }
    }]
  };

  return <ReactECharts option={option} style={{ height: `${height}px` }} />;
};