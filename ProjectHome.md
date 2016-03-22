游戏项目的服务器需要用到大量的定时器，特别是SLG游戏，造房子打仗都有一个时间CD的机制，因此需要有一个高效的timer实现。

linux中采用的定时器性能比较：
http://www.ibm.com/developerworks/cn/linux/l-cn-timers/

表 1. 定时器实现算法复杂度
|**实现方式**|**StartTimer**|**StopTimer**|**PerTickBookkeeping**|
|:-------|:-------------|:------------|:---------------------|
|基于链表	   |O(1)	         |O(n)	        |O(n)                  |
|基于排序链表	 |O(n)	         |O(1)	        |O(1)                  |
|基于最小堆	  |O(lgn)	       |O(1)	        |O(1)                  |
|基于时间轮	  |O(1)	         |O(1)	        |O(1)                  |

本项目实现了http://www.cse.wustl.edu/~cdgill/courses/cs6874/TimingWheels.pptppt中讲到的hierarchal-wheel-timer